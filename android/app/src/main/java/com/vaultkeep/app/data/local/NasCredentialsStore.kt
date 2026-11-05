package com.vaultkeep.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vaultkeep.app.domain.model.NasConfig
import com.vaultkeep.app.domain.model.NasProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NAS host/share/username/password, encrypted at rest via Android Keystore
 * (AES256-SIV for keys, AES256-GCM for values) — never plain DataStore/SharedPreferences,
 * per the spec's security requirement for NAS credentials.
 */
@Singleton
class NasCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nas_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(config: NasConfig) {
        prefs.edit()
            .putString("protocol", config.protocol.name)
            .putString("host", config.host)
            .putInt("port", config.port)
            .putString("share_or_path", config.shareOrPath)
            .putString("username", config.username)
            .putString("password", config.password)
            .putBoolean("use_https", config.useHttps)
            .apply()
    }

    fun load(): NasConfig? {
        val host = prefs.getString("host", null) ?: return null
        val protocolName = prefs.getString("protocol", null) ?: return null
        val protocol = runCatching { NasProtocol.valueOf(protocolName) }.getOrNull() ?: return null
        return NasConfig(
            protocol = protocol,
            host = host,
            port = prefs.getInt("port", if (protocol == NasProtocol.SMB) 445 else 443),
            shareOrPath = prefs.getString("share_or_path", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            useHttps = prefs.getBoolean("use_https", false),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
