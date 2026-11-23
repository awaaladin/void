package com.vaultkeep.app.domain.usecase

import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.FileEntryDao
import com.vaultkeep.app.data.local.entity.BackupRecordEntity
import com.vaultkeep.app.data.local.entity.FileEntryEntity
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.model.BackupStatus
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

/**
 * The one per-file decision both [RunUsbBackupUseCase] (SAF tree) and
 * [RunMediaStoreBackupUseCase] (MediaStore query) make, pulled out so
 * "check incremental skip, hash, write, verify, record in Room" exists
 * exactly once rather than twice with two traversal mechanisms around it.
 */
class BackupFileWriter @Inject constructor(
    private val fileEntryDao: FileEntryDao,
    private val backupRecordDao: BackupRecordDao,
) {
    sealed class Outcome {
        data class Skipped(val relativePath: String) : Outcome()
        data class Copied(val relativePath: String, val bytesWritten: Long) : Outcome()
        data class Failed(val relativePath: String, val message: String) : Outcome()
    }

    suspend fun process(
        relativePath: String,
        sizeBytes: Long,
        modifiedAt: Long,
        setType: BackupSetKind,
        sourceUri: String,
        destination: BackupDestination,
        encrypted: Boolean,
        openInput: () -> InputStream,
    ): Outcome {
        val existingEntry = fileEntryDao.findByRelativePath(relativePath)
        val existingRecord = existingEntry?.let { backupRecordDao.find(it.id, destination.id) }

        val skip = IncrementalPolicy.shouldSkip(
            known = existingEntry?.let {
                IncrementalPolicy.KnownFile(
                    sizeBytes = it.sizeBytes,
                    modifiedAt = it.modifiedAt,
                    sha256 = it.sha256,
                    recordStatusForDestination = existingRecord?.status,
                )
            },
            candidate = IncrementalPolicy.CandidateFile(sizeBytes = sizeBytes, modifiedAt = modifiedAt),
        )
        if (skip) return Outcome.Skipped(relativePath)

        val sourceHash = hashOf(openInput)
        val result = destination.writeFile(relativePath, sizeBytes, sourceHash, openInput)

        val now = System.currentTimeMillis()
        val fileId = fileEntryDao.upsert(
            FileEntryEntity(
                id = existingEntry?.id ?: 0,
                uri = sourceUri,
                relativePath = relativePath,
                sizeBytes = sizeBytes,
                modifiedAt = modifiedAt,
                sha256 = sourceHash,
                setType = setType,
            ),
        )

        return when (result) {
            is BackupDestination.WriteResult.Success -> {
                val verified = IncrementalPolicy.isVerified(
                    sourceSha256 = sourceHash,
                    sourceSize = sizeBytes,
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
                        encrypted = encrypted,
                    ),
                )
                if (verified) Outcome.Copied(relativePath, result.bytesWritten) else Outcome.Failed(relativePath, "Verification failed")
            }
            is BackupDestination.WriteResult.Failure -> {
                backupRecordDao.upsert(
                    BackupRecordEntity(
                        id = existingRecord?.id ?: 0,
                        fileId = fileId,
                        destinationId = destination.id,
                        status = BackupStatus.FAILED,
                        remoteKey = null,
                        lastError = result.message,
                        updatedAt = now,
                        encrypted = encrypted,
                    ),
                )
                Outcome.Failed(relativePath, result.message)
            }
        }
    }

    private fun hashOf(openInput: () -> InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        openInput().use { input ->
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
