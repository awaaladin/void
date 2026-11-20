package com.vaultkeep.app.data.crypto

import com.vaultkeep.app.domain.BackupSource
import java.io.InputStream

/**
 * Wraps any [BackupSource] to transparently decrypt file content on the way
 * out — the read-side mirror of [EncryptingBackupDestination]. Only applies
 * [EncryptingStream.unwrap] when the caller says the specific file being
 * read was encrypted (per-record, via `backup_records.encrypted` — not a
 * blanket "encryption is currently on" check, since that setting can change
 * after older files were already written under the old one).
 */
class DecryptingBackupSource(
    private val delegate: BackupSource,
    private val keyManager: CryptoKeyManager,
) : BackupSource {

    override val id: String get() = delegate.id

    suspend fun readFile(remoteKey: String, encrypted: Boolean): InputStream {
        val raw = delegate.readFile(remoteKey)
        if (!encrypted) return raw

        if (!keyManager.isUnlocked()) {
            raw.close()
            error("This file is encrypted — unlock Void with your passphrase before restoring it.")
        }
        return EncryptingStream.unwrap(raw, keyManager)
    }

    override suspend fun readFile(remoteKey: String): InputStream = readFile(remoteKey, encrypted = true)
}
