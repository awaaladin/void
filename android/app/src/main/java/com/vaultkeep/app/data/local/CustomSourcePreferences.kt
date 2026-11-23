package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaultkeep.app.domain.model.BackupSetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customSourceDataStore by preferencesDataStore(name = "custom_sources")

/**
 * SAF tree URIs for the backup sets that have no MediaStore collection of
 * their own — Documents and arbitrary Downloads aren't reliably readable
 * through MediaStore under scoped storage the way Photos/Videos are (no
 * READ_MEDIA_* equivalent exists for them), and WhatsApp media has no
 * MediaStore collection at all. The honest fix is the same one USB/SSD
 * already uses: the user picks the real folder once via SAF, and every
 * backup run after that walks it like any other source.
 */
@Singleton
class CustomSourcePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun keyFor(kind: BackupSetKind) = stringPreferencesKey("source_uri_${kind.name}")

    fun sourceUri(kind: BackupSetKind): Flow<String?> =
        context.customSourceDataStore.data.map { it[keyFor(kind)] }

    suspend fun setSourceUri(kind: BackupSetKind, uri: String) {
        context.customSourceDataStore.edit { it[keyFor(kind)] = uri }
    }
}
