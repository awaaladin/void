package com.vaultkeep.app.data.crypto

import com.vaultkeep.app.data.local.EncryptionPreferences
import com.vaultkeep.app.domain.BackupDestination
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** One place every backup ViewModel calls to apply the encryption setting, instead of three copies of the same `if`. */
@Singleton
class DestinationEncryptor @Inject constructor(
    private val encryptionPreferences: EncryptionPreferences,
    private val keyManager: CryptoKeyManager,
) {
    suspend fun wrap(destination: BackupDestination): BackupDestination {
        val enabled = encryptionPreferences.isEnabled.first()
        return if (enabled) EncryptingBackupDestination(destination, keyManager) else destination
    }
}
