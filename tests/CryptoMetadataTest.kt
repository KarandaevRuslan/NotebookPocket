import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.security.NotebookCrypto

fun main() {
    val source = """<notebook><title>Original</title><script type="module">display(123)</script></notebook>"""
    val meta = NotebookMetadata("Recipe & more", "Detailed", "I. Author", "2.1.0")
    val newSource = NotebookMetadataCodec.update(source, meta)
    check(NotebookMetadataCodec.read(newSource) == meta)
    check(newSource.contains("display(123)"))
    val password = "safe-passphrase".toCharArray()
    val salt = NotebookCrypto.randomBytes(16)
    val key = NotebookCrypto.derive(password, salt)
    val nonce = NotebookCrypto.randomBytes(12)
    val publicMeta = "title=Recipe & more;author=I. Author;version=2.1.0".toByteArray()
    val ciphertext = NotebookCrypto.encrypt(newSource.toByteArray(), key, nonce, publicMeta)
    check(NotebookCrypto.decrypt(ciphertext, key, nonce, publicMeta).toString(Charsets.UTF_8) == newSource)
    check(runCatching { NotebookCrypto.decrypt(ciphertext, key, nonce, "title=Other".toByteArray()) }.isFailure)
    val otherKey = NotebookCrypto.derive("wrong-passphrase".toCharArray(), salt)
    check(runCatching { NotebookCrypto.decrypt(ciphertext, otherKey, nonce, publicMeta) }.isFailure)
    check(runCatching { NotebookCrypto.decrypt(ciphertext, key, nonce, publicMeta + byteArrayOf(1)) }.isFailure)
    println("PASS: author/version, source preservation, tampered metadata, wrong password")
}
