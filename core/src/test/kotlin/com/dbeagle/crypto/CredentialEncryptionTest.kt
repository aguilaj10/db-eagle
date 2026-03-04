package com.dbeagle.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CredentialEncryptionTest {

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val plaintext = "super-secret-password-123"
        val masterPassword = "master-key-456"

        val encrypted = CredentialEncryption.encrypt(plaintext, masterPassword)
        val decrypted = CredentialEncryption.decrypt(encrypted, masterPassword)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong master password fails`() {
        val plaintext = "my-database-password"
        val correctPassword = "correct-master-password"
        val wrongPassword = "wrong-master-password"

        val encrypted = CredentialEncryption.encrypt(plaintext, correctPassword)

        assertFailsWith<IllegalArgumentException> {
            CredentialEncryption.decrypt(encrypted, wrongPassword)
        }
    }

    @Test
    fun `ciphertext differs from plaintext`() {
        val plaintext = "visible-text"
        val masterPassword = "encryption-key"

        val encrypted = CredentialEncryption.encrypt(plaintext, masterPassword)

        assertNotEquals(plaintext, String(encrypted.ciphertext, Charsets.UTF_8))
    }

    @Test
    fun `each encryption produces unique IV and salt`() {
        val plaintext = "same-plaintext"
        val masterPassword = "same-password"

        val encrypted1 = CredentialEncryption.encrypt(plaintext, masterPassword)
        val encrypted2 = CredentialEncryption.encrypt(plaintext, masterPassword)

        assertNotEquals(encrypted1.iv.contentHashCode(), encrypted2.iv.contentHashCode())
        assertNotEquals(encrypted1.salt.contentHashCode(), encrypted2.salt.contentHashCode())
        assertNotEquals(encrypted1.ciphertext.contentHashCode(), encrypted2.ciphertext.contentHashCode())
    }

    @Test
    fun `empty plaintext roundtrip`() {
        val plaintext = ""
        val masterPassword = "master-key"

        val encrypted = CredentialEncryption.encrypt(plaintext, masterPassword)
        val decrypted = CredentialEncryption.decrypt(encrypted, masterPassword)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `long plaintext roundtrip`() {
        val plaintext = "a".repeat(10000)
        val masterPassword = "master-key"

        val encrypted = CredentialEncryption.encrypt(plaintext, masterPassword)
        val decrypted = CredentialEncryption.decrypt(encrypted, masterPassword)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `special characters in plaintext roundtrip`() {
        val plaintext = "пароль!@#$%^&*()[]{}|\\;:'\"<>,.?/~`"
        val masterPassword = "master-key"

        val encrypted = CredentialEncryption.encrypt(plaintext, masterPassword)
        val decrypted = CredentialEncryption.decrypt(encrypted, masterPassword)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `multiple encrypt decrypt operations with different passwords`() {
        val plaintext1 = "password1"
        val plaintext2 = "password2"
        val master1 = "master1"
        val master2 = "master2"

        val encrypted1 = CredentialEncryption.encrypt(plaintext1, master1)
        val encrypted2 = CredentialEncryption.encrypt(plaintext2, master2)

        assertEquals(plaintext1, CredentialEncryption.decrypt(encrypted1, master1))
        assertEquals(plaintext2, CredentialEncryption.decrypt(encrypted2, master2))

        assertFailsWith<IllegalArgumentException> {
            CredentialEncryption.decrypt(encrypted1, master2)
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialEncryption.decrypt(encrypted2, master1)
        }
    }
}
