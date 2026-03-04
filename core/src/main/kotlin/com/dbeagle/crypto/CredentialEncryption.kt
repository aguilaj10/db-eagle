package com.dbeagle.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.symmetric.AES
import dev.whyoleg.cryptography.providers.jdk.JDK
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CredentialEncryption {
    private const val SALT_LENGTH_BYTES = 32
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

    private val cryptoProvider = CryptographyProvider.JDK
    private val secureRandom = SecureRandom()

    fun encrypt(
        plaintext: String,
        masterPassword: String,
    ): EncryptedData {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val derivedKey = deriveKey(masterPassword, salt)

        val aes = cryptoProvider.get(AES.GCM)
        val key = aes.keyDecoder().decodeFromBlocking(AES.Key.Format.RAW, derivedKey)

        val cipher = key.cipher()
        val ciphertext = cipher.encryptBlocking(plaintext.toByteArray(Charsets.UTF_8), iv)

        return EncryptedData(
            ciphertext = ciphertext,
            iv = iv,
            salt = salt,
        )
    }

    fun decrypt(
        encrypted: EncryptedData,
        masterPassword: String,
    ): String {
        val derivedKey = deriveKey(masterPassword, encrypted.salt)

        val aes = cryptoProvider.get(AES.GCM)
        val key = aes.keyDecoder().decodeFromBlocking(AES.Key.Format.RAW, derivedKey)

        val cipher = key.cipher()

        return try {
            val decryptedBytes = cipher.decryptBlocking(encrypted.ciphertext, encrypted.iv)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption failed: incorrect master password or corrupted data", e)
        }
    }

    private fun deriveKey(
        password: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
