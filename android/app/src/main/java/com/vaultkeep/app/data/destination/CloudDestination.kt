package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.CompleteUploadRequest
import com.vaultkeep.app.data.remote.CompletedPart
import com.vaultkeep.app.data.remote.CreateUploadRequest
import com.vaultkeep.app.data.remote.PresignPartRequest
import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.domain.BackupDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.security.MessageDigest

/**
 * Uploads directly to Void Cloud (R2/B2 behind our Django backend) via
 * presigned S3 multipart upload — file bytes go straight from this device
 * to the bucket; our API only ever hands out URLs and bookkeeping.
 */
class CloudDestination(
    private val api: VoidApi,
    private val uploadClient: OkHttpClient,
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
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val createResponse = api.createUpload(CreateUploadRequest(relativePath, sizeBytes))
            if (!createResponse.isSuccessful) {
                return@withContext BackupDestination.WriteResult.Failure("Could not start cloud upload: HTTP ${createResponse.code()}")
            }
            val created = createResponse.body()
                ?: return@withContext BackupDestination.WriteResult.Failure("Empty response starting cloud upload")

            val digest = MessageDigest.getInstance("SHA-256")
            val completedParts = mutableListOf<CompletedPart>()
            var partNumber = 1
            var bytesWritten = 0L
            val buffer = ByteArray(CHUNK_SIZE_BYTES)

            openInput().use { input ->
                while (true) {
                    val read = readFully(input, buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)

                    val presignResponse = api.presignPart(PresignPartRequest(created.fileId, partNumber))
                    if (!presignResponse.isSuccessful) {
                        abortQuietly(created.fileId)
                        return@withContext BackupDestination.WriteResult.Failure(
                            "Could not get an upload URL for part $partNumber: HTTP ${presignResponse.code()}",
                        )
                    }
                    val url = presignResponse.body()?.url
                        ?: return@withContext BackupDestination.WriteResult.Failure("Missing presigned URL for part $partNumber")

                    val putRequest = Request.Builder()
                        .url(url)
                        .put(buffer.toRequestBody(null, 0, read))
                        .build()
                    val putResponse = uploadClient.newCall(putRequest).execute()
                    val eTag = putResponse.use { resp ->
                        if (!resp.isSuccessful) null else resp.header("ETag")
                    }
                    if (eTag == null) {
                        abortQuietly(created.fileId)
                        return@withContext BackupDestination.WriteResult.Failure("Part $partNumber upload to storage failed")
                    }

                    completedParts += CompletedPart(partNumber = partNumber, eTag = eTag.trim('"'))
                    bytesWritten += read
                    partNumber++
                }
            }

            val completeResponse = api.completeUpload(CompleteUploadRequest(created.fileId, completedParts))
            if (!completeResponse.isSuccessful) {
                return@withContext BackupDestination.WriteResult.Failure("Could not finalize cloud upload: HTTP ${completeResponse.code()}")
            }

            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
            BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = bytesWritten)
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "Cloud upload failed")
        }
    }

    private suspend fun abortQuietly(fileId: String) {
        runCatching { api.abortUpload(com.vaultkeep.app.data.remote.AbortUploadRequest(fileId)) }
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
        // Comfortably above S3's 5MB minimum multipart part size.
        private const val CHUNK_SIZE_BYTES = 8 * 1024 * 1024
    }
}
