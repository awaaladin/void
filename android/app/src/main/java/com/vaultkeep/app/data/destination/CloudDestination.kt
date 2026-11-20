package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.AbortUploadRequest
import com.vaultkeep.app.data.remote.ApiErrorBody
import com.vaultkeep.app.data.remote.CompleteUploadRequest
import com.vaultkeep.app.data.remote.CreateUploadRequest
import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.domain.BackupDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.InputStream
import java.security.MessageDigest

/**
 * Uploads to Void Cloud (Vercel Blob behind our Django backend) in chunks.
 *
 * Not a direct phone-to-bucket upload like USB/NAS/the original S3 design:
 * Vercel Blob's Python SDK has no client-direct presigned-multipart flow
 * (that's JS-SDK-only), and every request to a Vercel Function is capped
 * at 4.5MB regardless of plan. So each chunk is POSTed to our own backend,
 * which stores it as its own private Blob object — see backup/storage.py
 * and backup/views.py on the Django side for the full reasoning. A dropped
 * connection only costs that one chunk's retry, same resumability property
 * the S3 design had, just implemented a level up.
 */
class CloudDestination(
    private val api: VoidApi,
    override val id: String = "cloud:void",
    override val displayName: String = "Void Cloud",
) : BackupDestination {

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val response = runCatching { api.listFiles() }.getOrNull() ?: return@withContext false
        if (!response.isSuccessful) return@withContext false
        response.body().orEmpty().any { it.relativePath == relativePath && it.status == "VERIFIED" }
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val response = api.usage()
        if (!response.isSuccessful) {
            error("Void Cloud connection failed: HTTP ${response.code()} — sign in again if this persists.")
        }
    }

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        knownSha256: String?,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val createResponse = api.createUpload(CreateUploadRequest(relativePath, sizeBytes, knownSha256.orEmpty()))
            if (!createResponse.isSuccessful) {
                return@withContext BackupDestination.WriteResult.Failure(serverErrorMessage(createResponse, "Could not start cloud upload"))
            }
            val created = createResponse.body()
                ?: return@withContext BackupDestination.WriteResult.Failure("Empty response starting cloud upload")

            if (created.deduped) {
                // The backend already linked this file to an existing VERIFIED upload's chunks —
                // identical content, nothing left to send.
                return@withContext BackupDestination.WriteResult.Success(
                    writtenSha256 = knownSha256 ?: "",
                    bytesWritten = sizeBytes,
                )
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var chunkIndex = 0
            val buffer = ByteArray(CHUNK_SIZE_BYTES)

            openInput().use { input ->
                while (true) {
                    val read = readFully(input, buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)

                    val chunkBody = buffer.copyOfRange(0, read).toRequestBody(OCTET_STREAM)
                    val chunkResponse = api.uploadChunk(created.fileId, chunkIndex, chunkBody)
                    if (!chunkResponse.isSuccessful) {
                        abortQuietly(created.fileId)
                        return@withContext BackupDestination.WriteResult.Failure(
                            serverErrorMessage(chunkResponse, "Chunk $chunkIndex upload failed"),
                        )
                    }
                    chunkIndex++
                }
            }

            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
            val completeResponse = api.completeUpload(CompleteUploadRequest(created.fileId, chunkIndex, hashHex))
            if (!completeResponse.isSuccessful) {
                return@withContext BackupDestination.WriteResult.Failure(serverErrorMessage(completeResponse, "Could not finalize cloud upload"))
            }
            val completed = completeResponse.body()
                ?: return@withContext BackupDestination.WriteResult.Failure("Empty response completing cloud upload")

            BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = completed.sizeBytes)
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "Cloud upload failed")
        }
    }

    private suspend fun abortQuietly(fileId: String) {
        runCatching { api.abortUpload(AbortUploadRequest(fileId)) }
    }

    /** Backend errors (quota rejections especially) come back as {"error": "..."} — surface
     * that real message instead of a bare HTTP code whenever the body parses. */
    private fun serverErrorMessage(response: Response<*>, fallbackPrefix: String): String {
        val body = response.errorBody()?.string()
        val parsed = body?.let { runCatching { json.decodeFromString<ApiErrorBody>(it) }.getOrNull() }
        return parsed?.error ?: "$fallbackPrefix: HTTP ${response.code()}"
    }

    /** InputStream.read(buffer) may return fewer bytes than requested even mid-stream; loop until the buffer is full or the stream ends. */
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    companion object {
        // Stays under Vercel Functions' 4.5MB per-request body cap with headroom to spare.
        private const val CHUNK_SIZE_BYTES = 4 * 1024 * 1024
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
