package com.vaultkeep.app.data.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the file-encryption key (the DEK) and the passphrase that protects
 * it. Two layers, matching the spec's "key from passphrase via PBKDF2,
 * wrapped by Android Keystore":
 *
 *  1. The DEK (random AES-256 key, generated once) is encrypted with a key
 *     derived from the user's passphrase via PBKDF2WithHmacSHA256
 *     (210,000 iterations — OWASP's 2023 minimum for PBKDF2-SHA256).
 *  2. That passphrase-wrapped DEK blob is stored in
 *     EncryptedSharedPreferences, which is itself backed by an Android
 *     Keystore master key — so recovering it needs both the device's
 *     Keystore (this phone) and the passphrase (the user's memory).
 *
 * Losing the passphrase is unrecoverable by design — there is no backdoor,
 * no reset, nothing this app or its developer can do. That has to be
 * surfaced clearly in the UI before a user sets one (see PassphraseSetupScreen).
 *
 * The DEK only ever lives in memory after a successful unlock, and only
 * for this process's lifetime — losing that is "locked", not "lost";
 * re-entering the same passphrase unlocks it again.
 */
@Singleton
class CryptoKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "crypto_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cachedDek: ByteArray? = null

    fun isPassphraseSet(): Boolean = prefs.contains(KEY_WRAPPED_DEK)

    fun isUnlocked(): Boolean = cachedDek != null

    /** Clears the in-memory key. The wrapped DEK on disk is untouched — unlock() with the same passphrase restores access. */
    fun lock() {
        cachedDek?.let { Arrays.fill(it, 0) }
        cachedDek = null
    }

    /**
     * First-time setup (or changing the passphrase on an existing DEK —
     * re-wraps the same DEK so already-encrypted files stay readable).
     */
    fun setPassphrase(passphrase: CharArray) {
        val dek = if (isPassphraseSet() && isUnlocked()) {
            cachedDek!! // re-wrap the existing DEK under the new passphrase
        } else {
            val fresh = ByteArray(DEK_LENGTH_BYTES)
            SecureRandom().nextBytes(fresh)
            fresh
        }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val passphraseKey = deriveKey(passphrase, salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, passphraseKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val wrappedDek = cipher.doFinal(dek)

        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_WRAPPED_DEK, Base64.encodeToString(wrappedDek, Base64.NO_WRAP))
            .apply()

        cachedDek = dek
    }

    /** Returns true and caches the DEK on a correct passphrase; false (not a crash) on a wrong one — GCM's auth tag makes a wrong key fail cleanly. */
    fun unlock(passphrase: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val iv = prefs.getString(KEY_WRAP_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val wrappedDek = prefs.getString(KEY_WRAPPED_DEK, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false

        val passphraseKey = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, passphraseKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cachedDek = cipher.doFinal(wrappedDek)
            true
        } catch (e: Exception) {
            false // wrong passphrase — GCM tag mismatch, not a bug
        }
    }

    /** Resets everything — a deliberate "forget this passphrase" action, not something to call on a failed unlock. */
    fun resetAll() {
        lock()
        prefs.edit().clear().apply()
    }

    internal fun dekOrThrow(): SecretKeySpec {
        val dek = cachedDek ?: error("Encryption is locked — enter your passphrase first.")
        return SecretKeySpec(dek, "AES")
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, DEK_LENGTH_BYTES * 8)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val KEY_SALT = "salt"
        private const val KEY_WRAP_IV = "wrap_iv"
        private const val KEY_WRAPPED_DEK = "wrapped_dek"

        private const val DEK_LENGTH_BYTES = 32 // AES-256
        private const val SALT_LENGTH_BYTES = 16
        internal const val GCM_IV_LENGTH_BYTES = 12
        internal const val GCM_TAG_LENGTH_BITS = 128
        internal const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val PBKDF2_ITERATIONS = 210_000 // OWASP 2023 minimum for PBKDF2-SHA256
    }
}
