package com.vaultkeep.app.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.FileEntryDao
import com.vaultkeep.app.data.local.entity.BackupRecordEntity
import com.vaultkeep.app.data.local.entity.FileEntryEntity
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.model.BackupStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
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
)

/**
 * Walks every file under [sourceTreeUri], decides per-file whether to skip
 * (via [IncrementalPolicy]) or copy to [destination], and keeps the Room
 * index (`file_entries` / `backup_records`) as the source of truth —
 * exactly the Phase 2 scope: SAF pick + incremental copy to USB/SSD.
 *
 * Deliberately synchronous/sequential for Phase 2; chunked resume and
 * WorkManager scheduling are added in Phase 3 without changing this
 * per-file decision logic.
 */
class RunUsbBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileEntryDao: FileEntryDao,
    private val backupRecordDao: BackupRecordDao,
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

        var copied = 0
        var skipped = 0
        var failed = 0
        var bytesCopied = 0L

        for ((index, pair) in files.withIndex()) {
            val (doc, relativePath) = pair
            val existingEntry = fileEntryDao.findByRelativePath(relativePath)
            val existingRecord = existingEntry?.let {
                backupRecordDao.find(it.id, destination.id)
            }

            val skip = IncrementalPolicy.shouldSkip(
                known = existingEntry?.let {
                    IncrementalPolicy.KnownFile(
                        sizeBytes = it.sizeBytes,
                        modifiedAt = it.modifiedAt,
                        sha256 = it.sha256,
                        recordStatusForDestination = existingRecord?.status,
                    )
                },
                candidate = IncrementalPolicy.CandidateFile(
                    sizeBytes = doc.length(),
                    modifiedAt = doc.lastModified(),
                ),
            )

            if (skip) {
                skipped++
                onProgress(
                    BackupProgressEvent(
                        currentFile = relativePath,
                        filesDone = index + 1,
                        totalFiles = files.size,
                        bytesDone = bytesCopied,
                        skipped = true,
                    ),
                )
                continue
            }

            val sourceHash = hashOf(doc)
            val result = destination.writeFile(relativePath, doc.length()) {
                context.contentResolver.openInputStream(doc.uri)
                    ?: error("Could not open source file $relativePath")
            }

            val now = System.currentTimeMillis()
            val fileId = fileEntryDao.upsert(
                FileEntryEntity(
                    id = existingEntry?.id ?: 0,
                    uri = doc.uri.toString(),
                    relativePath = relativePath,
                    sizeBytes = doc.length(),
                    modifiedAt = doc.lastModified(),
                    sha256 = sourceHash,
                    setType = setType,
                ),
            )

            when (result) {
                is BackupDestination.WriteResult.Success -> {
                    val verified = IncrementalPolicy.isVerified(
                        sourceSha256 = sourceHash,
                        sourceSize = doc.length(),
                        writtenSha256 = result.writtenSha256,
                        writtenSize = result.bytesWritten,
                    )
                    backupRecordDao.upsert(
                        BackupRecordEntity(
                            id = existingRecord?.id ?: 0,
                            fileId = fileId,
                            destinationId = destination.id,
                            status = if (verified) BackupStatus.VERIFIED else BackupStatus.FAILED,
                            remoteKey = relativePath,
                            lastError = if (verified) null else "Hash mismatch after copy",
                            updatedAt = now,
                        ),
                    )
                    if (verified) {
                        copied++
                        bytesCopied += result.bytesWritten
                    } else {
                        failed++
                    }
                    onProgress(
                        BackupProgressEvent(
                            currentFile = relativePath,
                            filesDone = index + 1,
                            totalFiles = files.size,
                            bytesDone = bytesCopied,
                            error = if (!verified) "Verification failed" else null,
                        ),
                    )
                }
                is BackupDestination.WriteResult.Failure -> {
                    failed++
                    backupRecordDao.upsert(
                        BackupRecordEntity(
                            id = existingRecord?.id ?: 0,
                            fileId = fileId,
                            destinationId = destination.id,
                            status = BackupStatus.FAILED,
                            remoteKey = null,
                            lastError = result.message,
                            updatedAt = now,
                        ),
                    )
                    onProgress(
                        BackupProgressEvent(
                            currentFile = relativePath,
                            filesDone = index + 1,
                            totalFiles = files.size,
                            bytesDone = bytesCopied,
                            error = result.message,
                        ),
                    )
                }
            }
        }

        BackupRunSummary(filesCopied = copied, filesSkipped = skipped, filesFailed = failed, bytesCopied = bytesCopied)
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

    private fun hashOf(doc: DocumentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(doc.uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
