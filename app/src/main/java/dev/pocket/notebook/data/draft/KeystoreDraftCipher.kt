package dev.pocket.notebook.data.draft

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All drafts, including unprotected notebooks, are encrypted under an installation-local key. */
internal class KeystoreDraftCipher : DraftCipher {
    companion object {
        private val keyGuard = Any()
    }

    private val alias = "notebook-pocket-drafts-v1"

    private fun key(): SecretKey =
        synchronized(keyGuard) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let {
                return it
            }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()
        }

    override fun seal(documentId: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(documentId.toByteArray(Charsets.UTF_8))
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun open(documentId: String, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 28) { "Invalid draft" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, ciphertext.copyOfRange(0, 12)),
        )
        cipher.updateAAD(documentId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
    }
}
