package dev.pocket.notebook.data.transfer

import dev.pocket.notebook.data.NotebookLockedException
import dev.pocket.notebook.data.catalog.LibraryOperations
import dev.pocket.notebook.data.collection.CollectionStore
import dev.pocket.notebook.data.document.DocumentContent
import dev.pocket.notebook.data.local.VfsNodeEntity
import dev.pocket.notebook.data.packing.*
import dev.pocket.notebook.data.resource.NotebookResources
import dev.pocket.notebook.data.storage.StorageAccess
import dev.pocket.notebook.data.storage.revision
import org.json.*

/** Tree export preserves opaque encrypted collections and resource namespaces. */
internal class LibraryArchive(
    private val storage: StorageAccess,
    private val collections: CollectionStore,
    private val content: DocumentContent,
    private val resources: NotebookResources,
    private val operations: LibraryOperations,
) {
    suspend fun encode(ids: List<String>): ByteArray {
        operations.requireExportable(ids)
        val files = linkedMapOf<String, ByteArray>()
        val nodes = JSONArray()
        suspend fun visit(n: VfsNodeEntity, parent: String?, depth: Int) {
            require(depth <= 100 && nodes.length() < 5000)
            val token = "n${nodes.length()}"
            var kind = n.kind
            var bytes: ByteArray? = null
            if (kind == "NOTEBOOK") {
                val owner = collections.parent(n)
                if (owner?.encrypted == true) {
                    kind = "COLLECTION"
                    val contents = collections.load(owner)
                    val subset =
                        PortableBundle.Collection(
                            mapOf(collections.memberKey(n) to content.portable(n)),
                            contents.resources,
                        )
                    val header =
                        PortableBundle.header(storage.files.read(owner.contentRef!!), "collection")
                    val key = storage.keyCache.get(owner.id) ?: throw NotebookLockedException()
                    try {
                        bytes =
                            PortableBundle.seal(
                                PortableBundle.encode(subset),
                                key,
                                header.salt,
                                "collection",
                                header.iterations,
                            )
                    } finally {
                        key.fill(0)
                    }
                } else bytes = resources.detached(n)
            } else if (kind == "COLLECTION") bytes = storage.files.read(n.contentRef!!)
            val path = bytes?.let { "objects/$token.bin" }
            nodes.put(
                JSONObject()
                    .put("id", token)
                    .put("parent", parent ?: JSONObject.NULL)
                    .put("kind", kind)
                    .put("title", n.title)
                    .put("file", path ?: JSONObject.NULL)
            )
            if (path != null) files[path] = bytes!!
            if (n.kind == "FOLDER")
                storage.dao.children(n.id).forEach { visit(it, token, depth + 1) }
        }
        operations.roots(ids).forEach { visit(it, null, 0) }
        files["manifest.json"] =
            JSONObject()
                .put("format", "pocket-library")
                .put("version", 1)
                .put("nodes", nodes)
                .toString()
                .toByteArray()
        return ArchiveFiles.pack(files)
    }

    suspend fun decode(bytes: ByteArray, parent: String?): List<String> {
        val entries = ArchiveFiles.unpack(bytes)
        val manifest = entries.getValue("manifest.json")
        require(manifest.size <= 2 * 1024 * 1024)
        val json = JSONObject(manifest.toString(Charsets.UTF_8))
        require(json.getString("format") == "pocket-library" && json.getInt("version") == 1)
        val nodes = json.getJSONArray("nodes")
        require(nodes.length() in 1..5000)
        val expected = mutableSetOf("manifest.json")
        val mapping = mutableMapOf<String, String>()
        val kinds = mutableMapOf<String, String>()
        val levels = mutableMapOf<String, Int>()
        val roots = mutableListOf<String>()
        return storage.revision().run { tx ->
            val destination = parent?.let { storage.required(it) }
            require(destination == null || destination.kind in setOf("FOLDER", "COLLECTION"))
            if (destination?.kind == "COLLECTION") collections.load(destination)
            for (i in 0 until nodes.length()) {
                val e = nodes.getJSONObject(i)
                val token = e.getString("id")
                require(Regex("n[0-9]{1,5}").matches(token) && token !in mapping)
                val kind = e.getString("kind")
                require(kind in setOf("FOLDER", "COLLECTION", "NOTEBOOK"))
                val p = if (e.isNull("parent")) null else e.getString("parent")
                val target =
                    if (p == null) parent else mapping[p] ?: error("Invalid archive parent")
                require(p == null || kinds[p] == "FOLDER")
                val level = if (p == null) 0 else levels.getValue(p) + 1
                require(level <= 100)
                require(
                    (target?.let { storage.required(it).kind }) != "COLLECTION" ||
                        kind == "NOTEBOOK"
                )
                val title = e.getString("title").trim()
                require(title.length in 1..10000)
                val path = if (e.isNull("file")) null else e.getString("file")
                require((kind == "FOLDER") == (path == null))
                val blob =
                    if (path == null) null
                    else {
                        require(path == "objects/$token.bin" && expected.add(path))
                        entries.getValue(path)
                    }
                val id = storage.newId()
                mapping[token] = id
                kinds[token] = kind
                levels[token] = level
                if (p == null) roots.add(id)
                val position = (storage.dao.lastPosition(target) ?: -1) + 1
                if (kind == "NOTEBOOK") {
                    val packet = NotebookPackage.unpack(blob!!)
                    val m = packet.metadata
                    val owner =
                        target?.let { storage.required(it) }?.takeIf { it.kind == "COLLECTION" }
                    val ref = if (owner != null) "member:$id" else tx.write(id, blob)
                    storage.dao.insert(
                        VfsNodeEntity(
                            id,
                            target,
                            kind,
                            position,
                            m.title,
                            m.description,
                            m.author,
                            m.version,
                            contentRef = ref,
                            packageFormat = true,
                            encrypted = packet.protected,
                            hasCover = packet.cover != null,
                        )
                    )
                    if (owner != null) {
                        val c = collections.load(owner)
                        collections.store(owner, c.copy(members = c.members + (id to blob)), tx)
                    }
                } else if (kind == "COLLECTION") {
                    val encrypted = PortableBundle.protected(blob!!)
                    if (encrypted) PortableBundle.header(blob, "collection")
                    else PortableBundle.decode(blob)
                    val node =
                        VfsNodeEntity(
                            id,
                            target,
                            kind,
                            position,
                            title,
                            contentRef = tx.write(id, blob),
                            packageFormat = true,
                            encrypted = encrypted,
                        )
                    storage.dao.insert(node)
                    if (!encrypted) collections.index(node)
                } else storage.dao.insert(VfsNodeEntity(id, target, kind, position, title))
            }
            require(entries.keys == expected && storage.dao.count() <= 10000)
            roots
        }
    }
}
