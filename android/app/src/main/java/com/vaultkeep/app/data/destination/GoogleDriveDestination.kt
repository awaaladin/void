package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.data.remote.DriveFileMetadataRequest
import com.vaultkeep.app.domain.BackupDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream
import java.security.MessageDigest

/**
 * Writes files to a dedicated "Void Backups" folder in the signed-in
 * user's own Google Drive. Uses the resumable upload protocol (one session
 * URI, then a single streamed PUT of the whole file) rather than one-shot
 * multipart — Google's own guidance caps multipart at small files, and
 * Void's own files (photos, videos) routinely aren't small. This does NOT
 * implement resuming a dropped upload from where it left off (that would
 * need persisting per-file upload progress) — a dropped connection just
 * fails that file for this run; the next run retries it whole, same as
 * any other destination's plain retry behavior.
 *
 * [accessToken] is a short-lived OAuth token obtained just before a backup
 * run via [com.vaultkeep.app.data.auth.DriveAuthManager] — not stored,
 * not refreshed mid-run. A backup long enough to outlast it (about an
 * hour) will see later files fail with a 401; re-running the backup gets
 * a fresh token and finishes the rest incrementally, same as any failure.
 */
class GoogleDriveDestination(
    private val api: DriveApi,
    private val rawHttpClient: OkHttpClient,
    accessToken: String,
    override val id: String = "drive:void",
    override val displayName: String = "Google Drive",
) : BackupDestination {

    private val bearer = "Bearer $accessToken"
    private val resolver = DriveFolderResolver(api, bearer)

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parentId = resolver.resolveParentFolder(relativePath, createIfMissing = false) ?: return@runCatching false
            val fileName = relativePath.substringAfterLast('/')
            resolver.findFile(fileName, parentId) != null
        }.getOrDefault(false)
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        resolver.rootFolder(createIfMissing = true)
            ?: error("Could not create or find the \"Void Backups\" folder in Drive")
    }

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        knownSha256: String?,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val parentId = resolver.resolveParentFolder(relativePath, createIfMissing = true)
                ?: return@withContext BackupDestination.WriteResult.Failure("Could not resolve Drive folder for '$relativePath'")
            val fileName = relativePath.substringAfterLast('/')
            val mimeType = mimeTypeFor(fileName)
            val existingFileId = resolver.findFile(fileName, parentId)

            val sessionResponse = if (existingFileId != null) {
                api.startResumableUpdate(bearer, existingFileId, mimeType, sizeBytes)
            } else {
                api.startResumableUpload(
                    bearer, mimeType, sizeBytes,
                    body = DriveFileMetadataRequest(name = fileName, parents = listOf(parentId)),
                )
            }
            if (!sessionResponse.isSuccessful) {
                return@withContext BackupDestination.WriteResult.Failure(
                    "Could not start Drive upload for '$fileName': HTTP ${sessionResponse.code()}",
                )
            }
            val sessionUri = sessionResponse.headers()["Location"]
                ?: return@withContext BackupDestination.WriteResult.Failure("Drive did not return an upload session for '$fileName'")

            val digest = MessageDigest.getInstance("SHA-256")
            val streamingBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = sizeBytes
                override fun writeTo(sink: BufferedSink) {
                    openInput().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                    }
                }
            }

            val putRequest = Request.Builder().url(sessionUri).put(streamingBody).build()
            rawHttpClient.newCall(putRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext BackupDestination.WriteResult.Failure(
                        "Drive upload failed for '$fileName': HTTP ${response.code}",
                    )
                }
            }

            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
            BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = sizeBytes)
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "Drive upload failed")
        }
    }

    private fun mimeTypeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
