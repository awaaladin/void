package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.vaultkeep.app.domain.model.BackupSetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupSetsDataStore by preferencesDataStore(name = "backup_sets")

/** Which backup sets the user has actually turned on — real, persisted state. */
@Singleton
class BackupSetsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun keyFor(kind: BackupSetKind) = booleanPreferencesKey("enabled_${kind.name}")

    fun isEnabled(kind: BackupSetKind): Flow<Boolean> =
        context.backupSetsDataStore.data.map { it[keyFor(kind)] ?: false }

    suspend fun setEnabled(kind: BackupSetKind, enabled: Boolean) {
        context.backupSetsDataStore.edit { it[keyFor(kind)] = enabled }
    }
}
