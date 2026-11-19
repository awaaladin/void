package com.vaultkeep.app.data.crypto

import com.vaultkeep.app.domain.BackupDestination
import java.io.InputStream
import java.security.MessageDigest

/**
 * Wraps any [BackupDestination] to transparently encrypt file content
 * before it reaches `delegate` — USB, NAS, and Void Cloud all get
 * encryption for free, with zero changes to their own code.
 *
 * Correctness note this class exists to get right: `delegate` only ever
 * sees ciphertext, so whatever hash *it* computes over what it actually
 * wrote is a ciphertext hash — comparing that against the caller's
 * plaintext `sourceHash` (which every call site already computes before
 * calling `writeFile`) would never match, and every encrypted backup
 * would wrongly show as FAILED. So this class always reports back the
 * *plaintext* hash/size in its own [BackupDestination.WriteResult],
 * regardless of what `delegate` saw — from the caller's point of view, an
 * encrypted destination looks exactly like a successful, verified write
 * of the original file.
 */
class EncryptingBackupDestination(
    private val delegate: BackupDestination,
    private val keyManager: CryptoKeyManager,
) : BackupDestination {

    override val id: String get() = delegate.id
    override val displayName: String get() = delegate.displayName

    override suspend fun exists(relativePath: String): Boolean = delegate.exists(relativePath)

    override suspend fun testConnection() = delegate.testConnection()

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        knownSha256: String?,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult {
        if (!keyManager.isUnlocked()) {
            return BackupDestination.WriteResult.Failure(
                "Encryption is on but locked — open Void and enter your passphrase before backing up.",
            )
        }

        // Almost always provided by the caller already; self-compute only as a fallback so this
        // class is correct even if some future call site forgets to pass it.
        val plaintextHash = knownSha256 ?: hashPlaintext(openInput)

        val encryptedSize = sizeBytes + EncryptingStream.ENCRYPTED_OVERHEAD_BYTES
        val result = delegate.writeFile(relativePath, encryptedSize, plaintextHash) {
            EncryptingStream.wrap(openInput(), keyManager)
        }

        return when (result) {
            is BackupDestination.WriteResult.Success ->
                BackupDestination.WriteResult.Success(writtenSha256 = plaintextHash, bytesWritten = sizeBytes)
            is BackupDestination.WriteResult.Failure -> result
        }
    }

    private fun hashPlaintext(openInput: () -> InputStream): String {
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
