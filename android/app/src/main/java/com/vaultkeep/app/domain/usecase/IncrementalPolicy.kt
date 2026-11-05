package com.vaultkeep.app.domain.usecase

import com.vaultkeep.app.domain.model.BackupStatus

/**
 * Pure decision logic for incremental backup — deliberately has no Android
 * framework or SAF dependency so it's cheaply unit-testable. The use case
 * that walks the SAF tree (RunUsbBackupUseCase) calls this per file.
 */
object IncrementalPolicy {

    data class KnownFile(
        val sizeBytes: Long,
        val modifiedAt: Long,
        val sha256: String?,
        val recordStatusForDestination: BackupStatus?,
    )

    data class CandidateFile(
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    /**
     * Skip re-copying a file to a destination only when:
     *  - we've seen this exact path before,
     *  - size AND modifiedAt are unchanged (cheap check, no re-hash needed), and
     *  - that destination already has a VERIFIED record for it.
     * Any mismatch (including a previously FAILED/PENDING record) triggers a
     * re-copy — never silently skip a file that isn't actually verified.
     */
    fun shouldSkip(known: KnownFile?, candidate: CandidateFile): Boolean {
        if (known == null) return false
        if (known.recordStatusForDestination != BackupStatus.VERIFIED) return false
        return known.sizeBytes == candidate.sizeBytes && known.modifiedAt == candidate.modifiedAt
    }

    /**
     * A file is considered verified only when both the size and the hash of
     * what was actually written match the source. Dedup by hash: if another
     * FileEntry already has this exact hash and is VERIFIED on the same
     * destination, we can link records without re-uploading bytes.
     */
    fun isVerified(sourceSha256: String, sourceSize: Long, writtenSha256: String, writtenSize: Long): Boolean =
        sourceSha256 == writtenSha256 && sourceSize == writtenSize
}
