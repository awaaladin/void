package com.vaultkeep.app.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultkeep.app.data.crypto.DecryptingBackupSource
import com.vaultkeep.app.data.local.dao.RestorableFileRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

data class RestoreProgressEvent(
    val currentFile: String,
    val filesDone: Int,
    val totalFiles: Int,
    val error: String? = null,
)

data class RestoreRunSummary(
    val filesRestored: Int,
    val filesFailed: Int,
    val bytesRestored: Long,
)

/**
 * The read-side mirror of [RunUsbBackupUseCase]: for each [RestorableFileRow],
 * reads it back from [source] (decrypting per-record, not per-current-setting
 * — see [DecryptingBackupSource]), writes it into [targetTreeUri] at its
 * original relative path, and verifies the restored bytes against the hash
 * recorded at backup time. A hash mismatch is reported as a failure for that
 * file rather than silently accepted.
 */
class RestoreUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun execute(
        files: List<RestorableFileRow>,
        source: DecryptingBackupSource,
        targetTreeUri: Uri,
        onProgress: suspend (RestoreProgressEvent) -> Unit,
    ): RestoreRunSummary = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, targetTreeUri)
            ?: error("Restore destination folder is no longer accessible: $targetTreeUri")

        var restored = 0
        var failed = 0
        var bytesRestored = 0L

        for ((index, file) in files.withIndex()) {
            try {
                val segments = file.relativePath.split("/").filter { it.isNotBlank() }
                require(segments.isNotEmpty()) { "Empty relative path" }

                var dir = root
                for (folderName in segments.dropLast(1)) {
                    dir = dir.findFile(folderName)?.takeIf { it.isDirectory }
                        ?: dir.createDirectory(folderName)
                        ?: error("Could not create folder '$folderName'")
                }

                val fileName = segments.last()
                dir.findFile(fileName)?.delete()
                val target = dir.createFile(mimeTypeFor(fileName), fileName)
                    ?: error("Could not create file '$fileName'")

                val digest = MessageDigest.getInstance("SHA-256")
                var bytesWritten = 0L
                context.contentResolver.openOutputStream(target.uri)?.use { out ->
                    source.readFile(file.remoteKey, file.encrypted).use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesWritten += read
                        }
                    }
                } ?: error("Could not open output stream for '$fileName'")

                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                val verified = file.sha256.isNullOrEmpty() || hashHex == file.sha256
                if (verified) {
                    restored++
                    bytesRestored += bytesWritten
                } else {
                    failed++
                    target.delete() // a corrupt restore is worse than a missing one
                }
                onProgress(
                    RestoreProgressEvent(
                        currentFile = file.relativePath,
                        filesDone = index + 1,
                        totalFiles = files.size,
                        error = if (!verified) "Hash mismatch after restore" else null,
                    ),
                )
            } catch (e: Exception) {
                failed++
                onProgress(
                    RestoreProgressEvent(
                        currentFile = file.relativePath,
                        filesDone = index + 1,
                        totalFiles = files.size,
                        error = e.message ?: "Restore failed",
                    ),
                )
            }
        }

        RestoreRunSummary(filesRestored = restored, filesFailed = failed, bytesRestored = bytesRestored)
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
