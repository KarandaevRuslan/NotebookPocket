package dev.pocket.notebook.data.packing

import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec
import dev.pocket.notebook.data.security.NotebookCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * Document portability format. Only public metadata and cover are unencrypted. The EXACT
 * manifest.json byte sequence authenticates ALL public metadata via AES-GCM AAD. coverSha256 in
 * this manifest binds cover image contents as well.
 */
object NotebookPackage {
    const val MAX_ARCHIVE = 64 * 1024 * 1024
    const val MAX_SOURCE = 2 * 1024 * 1024
    const val MAX_COVER = 1024 * 1024
    private val b64 = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    data class Packet(
        val metadata: NotebookMetadata,
        val cover: ByteArray?,
        val coverMime: String?,
        val protected: Boolean,
        val manifest: ByteArray,
        val payload: ByteArray,
        val salt: ByteArray?,
        val nonce: ByteArray?,
        val iterations: Int,
        val formatVersion: Int = 1,
    )

    fun pack(
        source: String,
        metadata: NotebookMetadata,
        cover: ByteArray?,
        coverMime: String?,
        passwordKey: ByteArray? = null,
        salt: ByteArray? = null,
        iterations: Int = NotebookCrypto.ITERATIONS,
        resources: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        NotebookMetadataCodec.validate(metadata)
        validateSource(source, metadata)
        ArchiveFiles.resources(resources)
        val htmlBytes = source.toByteArray(Charsets.UTF_8)
        val sourceBytes =
            ArchiveFiles.pack(
                linkedMapOf("content.html" to htmlBytes).apply {
                    resources.forEach { (path, data) ->
                        put("resources/${ArchiveFiles.path(path)}", data)
                    }
                }
            )
        require(htmlBytes.size <= MAX_SOURCE && sourceBytes.size <= MAX_ARCHIVE - 16384)
        validateCover(cover, coverMime)
        val encrypted = passwordKey != null
        val nonce = if (encrypted) NotebookCrypto.randomBytes(NotebookCrypto.NONCE_LENGTH) else null
        val manifestObject =
            JSONObject()
                .put("format", "notebook-pocket")
                .put("formatVersion", 2)
                .put("title", metadata.title)
                .put("description", metadata.description)
                .put("author", metadata.author)
                .put("version", metadata.version)
                .put("coverMime", coverMime ?: JSONObject.NULL)
                .put("coverSha256", cover?.let(NotebookCrypto::sha256) ?: JSONObject.NULL)
                .put("encrypted", encrypted)
        if (encrypted) {
            require(passwordKey!!.size == 32 && salt?.size == NotebookCrypto.SALT_LENGTH)
            manifestObject.put(
                "crypto",
                JSONObject()
                    .put("alg", "AES-256-GCM")
                    .put("kdf", "PBKDF2-HMAC-SHA256")
                    .put("iterations", iterations)
                    .put("salt", b64.encodeToString(salt))
                    .put("nonce", b64.encodeToString(nonce)),
            )
        }
        val manifestBytes = manifestObject.toString().toByteArray(Charsets.UTF_8)
        val payload =
            if (encrypted)
                NotebookCrypto.encrypt(sourceBytes, passwordKey!!, nonce!!, manifestBytes)
            else sourceBytes
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("manifest.json", manifestBytes)
            put(if (encrypted) "content.enc" else "content.html", payload)
            if (cover != null) put("cover.img", cover)
        }
        return output.toByteArray().also { require(it.size <= MAX_ARCHIVE) }
    }

    fun unpack(bytes: ByteArray): Packet {
        require(bytes.size <= MAX_ARCHIVE && bytes.size >= 4) { "Archive is too large or empty" }
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(
                    !entry.isDirectory &&
                        entry.name in
                            setOf("manifest.json", "content.enc", "content.html", "cover.img")
                ) {
                    "Unexpected ZIP entry"
                }
                require(!files.containsKey(entry.name)) { "Duplicate ZIP entry" }
                val max =
                    when (entry.name) {
                        "manifest.json" -> 16 * 1024
                        "cover.img" -> MAX_COVER
                        else -> MAX_ARCHIVE
                    }
                files[entry.name] = ArchiveFiles.read(zip, max)
                zip.closeEntry()
            }
        }
        val manifest = files["manifest.json"] ?: error("Missing manifest")
        val json = JSONObject(manifest.toString(Charsets.UTF_8))
        require(
            json.getString("format") == "notebook-pocket" && json.getInt("formatVersion") in 1..2
        )
        val meta =
            NotebookMetadata(
                json.getString("title"),
                json.getString("description"),
                json.getString("author"),
                json.getString("version"),
            )
        NotebookMetadataCodec.validate(meta)
        val cover = files["cover.img"]
        val mime = if (json.isNull("coverMime")) null else json.getString("coverMime")
        validateCover(cover, mime)
        val expectedDigest = if (json.isNull("coverSha256")) null else json.getString("coverSha256")
        require((cover?.let(NotebookCrypto::sha256)) == expectedDigest) {
            "Cover authentication failed"
        }
        val encrypted = json.getBoolean("encrypted")
        val payload =
            (if (encrypted) files["content.enc"] else files["content.html"])
                ?: error("Missing content")
        require(
            files.keys ==
                (setOf("manifest.json", if (encrypted) "content.enc" else "content.html") +
                    (if (cover == null) emptySet() else setOf("cover.img")))
        )
        val crypto = if (encrypted) json.getJSONObject("crypto") else null
        if (crypto != null)
            require(
                crypto.getString("alg") == "AES-256-GCM" &&
                    crypto.getString("kdf") == "PBKDF2-HMAC-SHA256"
            )
        val iterations = crypto?.getInt("iterations") ?: 0
        if (encrypted) require(iterations in NotebookCrypto.ITERATIONS..2_000_000)
        val salt = crypto?.getString("salt")?.let(decoder::decode)
        val nonce = crypto?.getString("nonce")?.let(decoder::decode)
        if (encrypted)
            require(
                salt?.size == NotebookCrypto.SALT_LENGTH &&
                    nonce?.size == NotebookCrypto.NONCE_LENGTH
            )
        val packet =
            Packet(
                meta,
                cover,
                mime,
                encrypted,
                manifest,
                payload,
                salt,
                nonce,
                iterations,
                json.getInt("formatVersion"),
            )
        if (!encrypted) validateSource(contents(packet).first, meta)
        return packet
    }

    fun contents(packet: Packet, key: ByteArray? = null): Pair<String, Map<String, ByteArray>> {
        val plain =
            if (packet.protected)
                NotebookCrypto.decrypt(
                    packet.payload,
                    requireNotNull(key),
                    requireNotNull(packet.nonce),
                    packet.manifest,
                )
            else packet.payload
        val result =
            if (packet.formatVersion == 1) plain.toString(Charsets.UTF_8) to emptyMap()
            else {
                val entries = ArchiveFiles.unpack(plain)
                require(entries.keys.all { it == "content.html" || it.startsWith("resources/") })
                entries.getValue("content.html").toString(Charsets.UTF_8) to
                    entries
                        .filterKeys { it.startsWith("resources/") }
                        .mapKeys { ArchiveFiles.path(it.key.removePrefix("resources/")) }
            }
        ArchiveFiles.resources(result.second)
        validateSource(result.first, packet.metadata)
        return result
    }

    fun decrypt(packet: Packet, derivedKey: ByteArray): String = contents(packet, derivedKey).first

    private fun validateSource(source: String, metadata: NotebookMetadata) {
        require(
            source.toByteArray(Charsets.UTF_8).size <= MAX_SOURCE &&
                Regex("<notebook(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(source)
        ) {
            "Invalid notebook HTML"
        }
        require(NotebookMetadataCodec.read(source) == metadata) {
            "Manifest does not match document metadata"
        }
    }

    fun validateCover(bytes: ByteArray?, mime: String?) {
        require((bytes == null) == (mime == null))
        if (bytes == null) return
        require(
            bytes.size in 1..MAX_COVER && mime in setOf("image/jpeg", "image/png", "image/webp")
        )
        val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png =
            bytes.size >= 8 &&
                bytes
                    .copyOfRange(0, 8)
                    .contentEquals(
                        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                    )
        val webp =
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        require(
            (mime == "image/jpeg" && jpeg) ||
                (mime == "image/png" && png) ||
                (mime == "image/webp" && webp)
        )
    }
}
