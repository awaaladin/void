package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.encryptionDataStore by preferencesDataStore(name = "encryption_settings")

/** Whether to encrypt files before sending them to any destination. On by default, per spec. */
@Singleton
class EncryptionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val enabledKey = booleanPreferencesKey("encryption_enabled")

    val isEnabled: Flow<Boolean> = context.encryptionDataStore.data.map { it[enabledKey] ?: true }

    suspend fun setEnabled(value: Boolean) {
        context.encryptionDataStore.edit { it[enabledKey] = value }
    }
}
