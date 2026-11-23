package com.vaultkeep.app.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultkeep.app.data.crypto.EncryptingBackupDestination
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupProgressEvent(
    val currentFile: String,
    val filesDone: Int,
    val totalFiles: Int,
    val bytesDone: Long,
    val skipped: Boolean = false,
    val error: String? = null,
)

data class BackupRunSummary(
    val filesCopied: Int,
    val filesSkipped: Int,
    val filesFailed: Int,
    val bytesCopied: Long,
    /** The first failure's actual reason — e.g. "Encryption is on but locked..." — so a
     * failed run can tell the user something more useful than a bare file count. */
    val firstError: String? = null,
)

/**
 * Walks every file under [sourceTreeUri], decides per-file whether to skip
 * (via [IncrementalPolicy]) or copy to [destination], and keeps the Room
 * index (`file_entries` / `backup_records`) as the source of truth —
 * exactly the Phase 2 scope: SAF pick + incremental copy to USB/SSD.
 * Per-file logic itself lives in [BackupFileWriter], shared with
 * [RunMediaStoreBackupUseCase] (Photos/Videos), which walks MediaStore
 * instead of a SAF tree.
 */
class RunUsbBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileWriter: BackupFileWriter,
) {
    suspend fun execute(
        sourceTreeUri: Uri,
        destination: BackupDestination,
        setType: BackupSetKind,
        onProgress: suspend (BackupProgressEvent) -> Unit,
    ): BackupRunSummary = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, sourceTreeUri)
            ?: error("Source tree is no longer accessible: $sourceTreeUri")

        val files = mutableListOf<Pair<DocumentFile, String>>()
        collectFiles(root, relativePathPrefix = "", out = files)

        val encrypted = destination is EncryptingBackupDestination

        var copied = 0
        var skipped = 0
        var failed = 0
        var bytesCopied = 0L
        var firstError: String? = null

        for ((index, pair) in files.withIndex()) {
            val (doc, relativePath) = pair

            val outcome = fileWriter.process(
                relativePath = relativePath,
                sizeBytes = doc.length(),
                modifiedAt = doc.lastModified(),
                setType = setType,
                sourceUri = doc.uri.toString(),
                destination = destination,
                encrypted = encrypted,
                openInput = {
                    context.contentResolver.openInputStream(doc.uri)
                        ?: error("Could not open source file $relativePath")
                },
            )

            when (outcome) {
                is BackupFileWriter.Outcome.Skipped -> {
                    skipped++
                    onProgress(BackupProgressEvent(relativePath, index + 1, files.size, bytesCopied, skipped = true))
                }
                is BackupFileWriter.Outcome.Copied -> {
                    copied++
                    bytesCopied += outcome.bytesWritten
                    onProgress(BackupProgressEvent(relativePath, index + 1, files.size, bytesCopied))
                }
                is BackupFileWriter.Outcome.Failed -> {
                    failed++
                    firstError = firstError ?: outcome.message
                    onProgress(BackupProgressEvent(relativePath, index + 1, files.size, bytesCopied, error = outcome.message))
                }
            }
        }

        BackupRunSummary(filesCopied = copied, filesSkipped = skipped, filesFailed = failed, bytesCopied = bytesCopied, firstError = firstError)
    }

    private fun collectFiles(dir: DocumentFile, relativePathPrefix: String, out: MutableList<Pair<DocumentFile, String>>) {
        for (child in dir.listFiles()) {
            val childPath = if (relativePathPrefix.isEmpty()) child.name.orEmpty() else "$relativePathPrefix/${child.name.orEmpty()}"
            if (child.isDirectory) {
                collectFiles(child, childPath, out)
            } else if (child.isFile) {
                out.add(child to childPath)
            }
        }
    }
}
