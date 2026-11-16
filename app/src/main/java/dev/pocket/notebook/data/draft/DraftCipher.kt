package dev.pocket.notebook.data.draft

/** Device-local encryption boundary; never shares keys or state with a WebView. */
internal interface DraftCipher {
    fun seal(documentId: String, plaintext: ByteArray): ByteArray

    fun open(documentId: String, ciphertext: ByteArray): ByteArray
}
