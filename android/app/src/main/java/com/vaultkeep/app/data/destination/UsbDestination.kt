package com.vaultkeep.app.data.destination

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultkeep.app.domain.BackupDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/**
 * Writes files into a user-picked SAF tree (USB/OTG SSD or flash drive, or
 * any other DocumentsProvider the system exposes — no root, no raw file
 * paths). [treeUri] must already have a persisted read/write permission
 * (see BackupSetupViewModel, which calls takePersistableUriPermission right
 * after the ACTION_OPEN_DOCUMENT_TREE result).
 */
class UsbDestination(
    private val context: Context,
    override val id: String,
    override val displayName: String,
    private val treeUri: Uri,
) : BackupDestination {

    private fun rootDocument(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Destination tree is no longer accessible: $treeUri")

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        resolveExisting(relativePath) != null
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val root = rootDocument()
        if (!root.exists() || !root.canWrite()) {
            error("Destination folder is not writable: $treeUri")
        }
    }

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val segments = relativePath.split("/").filter { it.isNotBlank() }
            require(segments.isNotEmpty()) { "Empty relative path" }

            var dir = rootDocument()
            for (folderName in segments.dropLast(1)) {
                dir = dir.findFile(folderName)?.takeIf { it.isDirectory }
                    ?: dir.createDirectory(folderName)
                    ?: return@withContext BackupDestination.WriteResult.Failure(
                        "Could not create folder '$folderName' on destination",
                    )
            }

            val fileName = segments.last()
            // Replace any existing file at this path rather than leaving stale duplicates.
            dir.findFile(fileName)?.delete()
            val target = dir.createFile(mimeTypeFor(fileName), fileName)
                ?: return@withContext BackupDestination.WriteResult.Failure(
                    "Could not create file '$fileName' on destination",
                )

            val digest = MessageDigest.getInstance("SHA-256")
            var bytesWritten = 0L
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                openInput().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesWritten += read
                    }
                }
            } ?: return@withContext BackupDestination.WriteResult.Failure(
                "Could not open output stream for '$fileName'",
            )

            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
            BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = bytesWritten)
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "Unknown error writing file")
        }
    }

    private fun resolveExisting(relativePath: String): DocumentFile? {
        val segments = relativePath.split("/").filter { it.isNotBlank() }
        var node: DocumentFile = rootDocument()
        for (segment in segments) {
            node = node.findFile(segment) ?: return null
        }
        return node
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
