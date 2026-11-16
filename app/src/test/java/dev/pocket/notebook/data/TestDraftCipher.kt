package dev.pocket.notebook.data

import dev.pocket.notebook.data.draft.DraftCipher
import dev.pocket.notebook.data.security.NotebookCrypto

/** Test-only key shared by recreated repository instances. Production exclusively uses AndroidKeyStore. */
internal object TestDraftCipher : DraftCipher {
    private val key = ByteArray(32) { (it + 1).toByte() }
    override fun seal(documentId: String, plaintext: ByteArray): ByteArray {
        val nonce = NotebookCrypto.randomBytes(12)
        return nonce + NotebookCrypto.encrypt(plaintext, key, nonce, documentId.toByteArray())
    }
    override fun open(documentId: String, ciphertext: ByteArray): ByteArray =
        NotebookCrypto.decrypt(ciphertext.copyOfRange(12, ciphertext.size), key, ciphertext.copyOfRange(0,12), documentId.toByteArray())
}
