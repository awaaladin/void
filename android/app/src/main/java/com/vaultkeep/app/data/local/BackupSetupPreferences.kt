package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupSetupDataStore by preferencesDataStore(name = "backup_setup")

/**
 * Persists the two SAF tree URIs the Phase 2 flow needs (source folder,
 * USB/SSD destination folder) so the user doesn't have to re-pick them
 * every time they open the app. Permission on these URIs is made
 * persistent separately via ContentResolver.takePersistableUriPermission.
 */
@Singleton
class BackupSetupPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sourceUriKey = stringPreferencesKey("source_tree_uri")
    private val destinationUriKey = stringPreferencesKey("destination_tree_uri")

    val sourceUri: Flow<String?> = context.backupSetupDataStore.data.map { it[sourceUriKey] }
    val destinationUri: Flow<String?> = context.backupSetupDataStore.data.map { it[destinationUriKey] }

    suspend fun setSourceUri(uri: String) {
        context.backupSetupDataStore.edit { it[sourceUriKey] = uri }
    }

    suspend fun setDestinationUri(uri: String) {
        context.backupSetupDataStore.edit { it[destinationUriKey] = uri }
    }
}
