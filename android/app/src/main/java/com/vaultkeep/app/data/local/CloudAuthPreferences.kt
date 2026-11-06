package com.vaultkeep.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Void Cloud JWT access/refresh tokens — Keystore-encrypted, same pattern as NAS credentials. */
@Singleton
class CloudAuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "cloud_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString("access", access).putString("refresh", refresh).apply()
    }

    fun saveAccessToken(access: String) {
        prefs.edit().putString("access", access).apply()
    }

    fun accessToken(): String? = prefs.getString("access", null)
    fun refreshToken(): String? = prefs.getString("refresh", null)
    fun isSignedIn(): Boolean = accessToken() != null

    fun clear() {
        prefs.edit().clear().apply()
    }
}
