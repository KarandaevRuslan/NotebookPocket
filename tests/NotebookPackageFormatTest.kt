import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.packing.NotebookPackage
import dev.pocket.notebook.data.security.NotebookCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** JVM test if org.json is on test classpath; Android's org.json works as well. */
fun main() {
    val meta = NotebookMetadata("Pancakes", "Description", "Author", "1.0")
    val source = NotebookMetadataCodec.update(
        "<notebook><title>Old</title><script type=\"module\">display(1)</script></notebook>", meta)
    val cover = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val salt = NotebookCrypto.randomBytes(16)
    val key = NotebookCrypto.derive("pancakes-passphrase".toCharArray(), salt)
    val bytes = NotebookPackage.pack(source, meta, cover, "image/png", key, salt)
    val packet = NotebookPackage.unpack(bytes)
    check(packet.protected && packet.metadata == meta)
    check(NotebookPackage.decrypt(packet, key) == source)
    check(packet.cover!!.contentEquals(cover))

    // Change ONLY cleartext title in manifest. Correct password must now fail.
    val corrupted = ByteArrayOutputStream()
    ZipOutputStream(corrupted).use { output ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val original = input.readBytes()
                val changed = if (entry.name == "manifest.json") {
                    original.toString(Charsets.UTF_8)
                        .replace("\"title\":\"Pancakes\"", "\"title\":\"Modified\"")
                        .toByteArray(Charsets.UTF_8)
                } else original
                output.putNextEntry(ZipEntry(entry.name))
                output.write(changed)
                output.closeEntry()
            }
        }
    }
    val tampered = NotebookPackage.unpack(corrupted.toByteArray())
    check(runCatching { NotebookPackage.decrypt(tampered, key) }.isFailure)
    val oldKey = key
    val newSalt = NotebookCrypto.randomBytes(16)
    val newKey = NotebookCrypto.derive("another-good-passphrase".toCharArray(), newSalt)
    val rotated = NotebookPackage.unpack(NotebookPackage.pack(source, meta, cover, "image/png", newKey, newSalt))
    check(runCatching { NotebookPackage.decrypt(rotated, oldKey) }.isFailure)
    check(NotebookPackage.decrypt(rotated, newKey) == source)
    println("PASS: ZIP roundtrip, metadata tamper, key rotation, cover SHA binding")
}
