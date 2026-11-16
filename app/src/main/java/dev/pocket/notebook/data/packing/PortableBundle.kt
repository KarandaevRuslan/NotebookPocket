package dev.pocket.notebook.data.packing

import dev.pocket.notebook.data.security.NotebookCrypto
import java.nio.ByteBuffer
import java.util.Base64
import org.json.JSONObject

/** A purpose-bound AES-GCM envelope. Keys and decrypted bundles never persist in caches. */
object PortableBundle {
    private val magic = byteArrayOf(78, 80, 88, 49) // NPX1

    data class Header(
        val bytes: ByteArray,
        val salt: ByteArray,
        val nonce: ByteArray,
        val iterations: Int,
        val offset: Int,
    )

    fun protected(bytes: ByteArray) =
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(magic)

    fun header(bytes: ByteArray, purpose: String): Header {
        require(protected(bytes) && bytes.size >= 8)
        val n = ByteBuffer.wrap(bytes, 4, 4).int
        require(n in 1..4096 && bytes.size >= 8 + n + 16)
        val data = bytes.copyOfRange(8, 8 + n)
        val json = JSONObject(data.toString(Charsets.UTF_8))
        require(
            json.getString("format") == "notebook-pocket-envelope" &&
                json.getInt("version") == 1 &&
                json.getString("purpose") == purpose
        )
        require(
            json.getString("algorithm") == "AES-256-GCM" &&
                json.getString("kdf") == "PBKDF2-HMAC-SHA256"
        )
        val salt = Base64.getDecoder().decode(json.getString("salt"))
        val nonce = Base64.getDecoder().decode(json.getString("nonce"))
        val iterations = json.getInt("iterations")
        require(
            salt.size == 16 && nonce.size == 12 && iterations in NotebookCrypto.ITERATIONS..2000000
        )
        return Header(data, salt, nonce, iterations, 8 + n)
    }

    fun seal(
        plain: ByteArray,
        key: ByteArray,
        salt: ByteArray,
        purpose: String,
        iterations: Int = NotebookCrypto.ITERATIONS,
    ): ByteArray {
        require(plain.size <= ArchiveFiles.MAX_TOTAL && key.size == 32 && salt.size == 16)
        val nonce = NotebookCrypto.randomBytes(12)
        val h =
            JSONObject()
                .put("format", "notebook-pocket-envelope")
                .put("version", 1)
                .put("purpose", purpose)
                .put("algorithm", "AES-256-GCM")
                .put("kdf", "PBKDF2-HMAC-SHA256")
                .put("iterations", iterations)
                .put("salt", Base64.getEncoder().encodeToString(salt))
                .put("nonce", Base64.getEncoder().encodeToString(nonce))
                .toString()
                .toByteArray()
        return magic +
            ByteBuffer.allocate(4).putInt(h.size).array() +
            h +
            NotebookCrypto.encrypt(plain, key, nonce, h)
    }

    fun open(bytes: ByteArray, key: ByteArray, purpose: String): ByteArray {
        val h = header(bytes, purpose)
        return NotebookCrypto.decrypt(
            bytes.copyOfRange(h.offset, bytes.size),
            key,
            h.nonce,
            h.bytes,
        )
    }

    data class Collection(
        val members: Map<String, ByteArray> = emptyMap(),
        val resources: Map<String, ByteArray> = emptyMap(),
    )

    fun encode(value: Collection): ByteArray =
        ArchiveFiles.pack(
            linkedMapOf(
                    "collection.json" to
                        "{\"format\":\"pocket-collection\",\"version\":1}".toByteArray()
                )
                .apply {
                    require(value.members.size <= 1000)
                    ArchiveFiles.resources(value.resources)
                    value.members.forEach { (id, bytes) ->
                        require(Regex("[a-zA-Z0-9_-]{1,120}").matches(id))
                        put("notebooks/$id.npkg", bytes)
                    }
                    value.resources.forEach { (path, bytes) ->
                        put("resources/${ArchiveFiles.path(path)}", bytes)
                    }
                }
        )

    fun decode(bytes: ByteArray): Collection {
        val entries = ArchiveFiles.unpack(bytes)
        val doc = JSONObject(entries.getValue("collection.json").toString(Charsets.UTF_8))
        require(doc.getString("format") == "pocket-collection" && doc.getInt("version") == 1)
        require(
            entries.keys.all {
                it == "collection.json" ||
                    Regex("notebooks/[a-zA-Z0-9_-]{1,120}\\.npkg").matches(it) ||
                    it.startsWith("resources/")
            }
        )
        val members =
            entries
                .filterKeys { it.startsWith("notebooks/") }
                .mapKeys { it.key.removePrefix("notebooks/").removeSuffix(".npkg") }
        require(members.size <= 1000)
        members.values.forEach { NotebookPackage.unpack(it) }
        ArchiveFiles.tree(
            entries
                .filterKeys { it.startsWith("resources/") }
                .keys
                .map { it.removePrefix("resources/") }
                .toSet()
        )
        return Collection(
                members,
                entries
                    .filterKeys { it.startsWith("resources/") }
                    .mapKeys { ArchiveFiles.path(it.key.removePrefix("resources/")) },
            )
            .also { ArchiveFiles.resources(it.resources) }
    }
}
