package com.vaultkeep.app.data.crypto

import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EncryptingStreamTest {

    private fun fakeKeyManager(keyBytes: ByteArray = ByteArray(32) { it.toByte() }): CryptoKeyManager {
        val keyManager = mockk<CryptoKeyManager>()
        every { keyManager.dekOrThrow() } returns SecretKeySpec(keyBytes, "AES")
        return keyManager
    }

    @Test
    fun `wrap then unwrap returns the exact original plaintext`() {
        val keyManager = fakeKeyManager()
        val plaintext = "Hello, Void encryption! This needs to round-trip exactly.".toByteArray()

        val encryptedBytes = EncryptingStream.wrap(ByteArrayInputStream(plaintext), keyManager).readBytes()
        val decrypted = EncryptingStream.unwrap(ByteArrayInputStream(encryptedBytes), keyManager).readBytes()

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted output size is exactly plaintext size plus the fixed overhead`() {
        val keyManager = fakeKeyManager()
        val plaintext = ByteArray(10_000) { (it % 256).toByte() }

        val encryptedBytes = EncryptingStream.wrap(ByteArrayInputStream(plaintext), keyManager).readBytes()

        assertEquals(plaintext.size + EncryptingStream.ENCRYPTED_OVERHEAD_BYTES, encryptedBytes.size)
    }

    @Test
    fun `empty file encrypts and decrypts correctly`() {
        val keyManager = fakeKeyManager()

        val encryptedBytes = EncryptingStream.wrap(ByteArrayInputStream(ByteArray(0)), keyManager).readBytes()
        assertEquals(EncryptingStream.ENCRYPTED_OVERHEAD_BYTES, encryptedBytes.size)

        val decrypted = EncryptingStream.unwrap(ByteArrayInputStream(encryptedBytes), keyManager).readBytes()
        assertEquals(0, decrypted.size)
    }

    @Test
    fun `encrypting the same plaintext twice produces different ciphertext (random IV)`() {
        val keyManager = fakeKeyManager()
        val plaintext = "same content both times".toByteArray()

        val first = EncryptingStream.wrap(ByteArrayInputStream(plaintext), keyManager).readBytes()
        val second = EncryptingStream.wrap(ByteArrayInputStream(plaintext), keyManager).readBytes()

        assertFalse("Ciphertext should differ run-to-run even for identical plaintext", first.contentEquals(second))
        // But both still decrypt back to the same original content.
        val decryptedFirst = EncryptingStream.unwrap(ByteArrayInputStream(first), keyManager).readBytes()
        val decryptedSecond = EncryptingStream.unwrap(ByteArrayInputStream(second), keyManager).readBytes()
        assertArrayEquals(decryptedFirst, decryptedSecond)
    }

    @Test(expected = Exception::class)
    fun `decrypting with the wrong key fails instead of returning garbage`() {
        val writerKeyManager = fakeKeyManager(ByteArray(32) { 1 })
        val readerKeyManager = fakeKeyManager(ByteArray(32) { 2 })
        val plaintext = "secret".toByteArray()

        val encryptedBytes = EncryptingStream.wrap(ByteArrayInputStream(plaintext), writerKeyManager).readBytes()
        // GCM's auth tag must make this throw, not silently decrypt to the wrong bytes.
        EncryptingStream.unwrap(ByteArrayInputStream(encryptedBytes), readerKeyManager).readBytes()
    }
}
