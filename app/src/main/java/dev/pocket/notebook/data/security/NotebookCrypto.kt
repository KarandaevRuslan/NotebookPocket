package dev.pocket.notebook.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password keys never leave this service. For portable archives, metadata bytes are AES-GCM AAD.
 */
object NotebookCrypto {
    const val ITERATIONS = 600_000
    const val SALT_LENGTH = 16
    const val NONCE_LENGTH = 12
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(salt.size == SALT_LENGTH && iterations in ITERATIONS..2_000_000)
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == NONCE_LENGTH)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(plaintext)
        }
    }

    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(key.size == 32 && nonce.size == NONCE_LENGTH)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(ciphertext) // Bad tag on wrong password OR modified public metadata.
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
