package com.vaultkeep.app.domain

import java.io.InputStream

/**
 * Pluggable transfer target. Phase 2 ships [com.vaultkeep.app.data.destination.UsbDestination];
 * NAS/Cloud/Drive implementations land in Phases 4-5 behind this same contract
 * so the backup use case never needs to know which one it's talking to.
 */
interface BackupDestination {
    val id: String
    val displayName: String

    /** True if a file already exists at this relative path on the destination. */
    suspend fun exists(relativePath: String): Boolean

    /**
     * Attempts a real round-trip (connect + authenticate, at minimum) and
     * throws with a descriptive message on failure. Unlike [exists], this
     * must NOT swallow exceptions — it's what setup screens call to report
     * "connection failed: <reason>" instead of a silent false.
     */
    suspend fun testConnection()

    /**
     * Writes [sizeBytes] from [openInput] to [relativePath], creating any
     * intermediate folders. Returns the SHA-256 of the bytes actually
     * written, so the caller can verify it against the source hash.
     *
     * [knownSha256] is the caller's already-computed plaintext hash, if it
     * has one (the USB/NAS/Cloud use case always does — it hashes the
     * source file before calling this). Most destinations ignore it; Cloud
     * uses it for upload-time dedup. It's always the *plaintext* hash, even
     * when [EncryptingBackupDestination][com.vaultkeep.app.data.crypto.EncryptingBackupDestination]
     * is in front of a destination — encryption never sees or changes it.
     */
    suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        knownSha256: String? = null,
        openInput: () -> InputStream,
    ): WriteResult

    sealed class WriteResult {
        data class Success(val writtenSha256: String, val bytesWritten: Long) : WriteResult()
        data class Failure(val message: String) : WriteResult()
    }
}
