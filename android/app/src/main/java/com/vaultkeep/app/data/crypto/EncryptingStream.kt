package com.vaultkeep.app.data.crypto

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps `plain` as: [12-byte random IV][AES-256-GCM ciphertext + 16-byte
 * auth tag]. Streams the encryption as bytes are read — never buffers the
 * whole file — so it works the same way for a 4KB document or a 2GB
 * video. The IV is part of the output stream (prefixed), not a separate
 * side-channel, so decryption only needs the DEK and these bytes.
 *
 * Output size is always exactly `plainSizeBytes + ENCRYPTED_OVERHEAD_BYTES`.
 */
object EncryptingStream {
    const val ENCRYPTED_OVERHEAD_BYTES = CryptoKeyManager.GCM_IV_LENGTH_BYTES + (CryptoKeyManager.GCM_TAG_LENGTH_BITS / 8)

    fun wrap(plain: InputStream, keyManager: CryptoKeyManager): InputStream {
        val key = keyManager.dekOrThrow()
        val iv = ByteArray(CryptoKeyManager.GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance(CryptoKeyManager.AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(CryptoKeyManager.GCM_TAG_LENGTH_BITS, iv))

        return SequenceInputStream(ByteArrayInputStream(iv), CipherInputStream(plain, cipher))
    }

    /**
     * The inverse of [wrap] — reads the IV prefix off `encrypted`, then
     * decrypts the rest. Not wired into any screen yet (that's Phase 7's
     * restore flow); it exists now so the format is tested and correct
     * before anything depends on it.
     */
    fun unwrap(encrypted: InputStream, keyManager: CryptoKeyManager): InputStream {
        val key = keyManager.dekOrThrow()
        val iv = ByteArray(CryptoKeyManager.GCM_IV_LENGTH_BYTES)
        var read = 0
        while (read < iv.size) {
            val n = encrypted.read(iv, read, iv.size - read)
            if (n == -1) error("Encrypted stream is shorter than the IV prefix — not a valid Void-encrypted file.")
            read += n
        }

        val cipher = Cipher.getInstance(CryptoKeyManager.AES_GCM_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoKeyManager.GCM_TAG_LENGTH_BITS, iv))
        return CipherInputStream(encrypted, cipher)
    }
}
