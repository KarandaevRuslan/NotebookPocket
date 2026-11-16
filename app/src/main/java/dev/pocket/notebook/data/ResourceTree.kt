package dev.pocket.notebook.data

import dev.pocket.notebook.data.packing.ArchiveFiles
import org.json.JSONArray
import org.json.JSONObject

/**
 * Virtual resource files plus explicit directories. The private record travels with every package.
 */
internal data class ResourceTree(
    val files: Map<String, ByteArray>,
    val directories: Set<String> = emptySet(),
) {
    fun folder(path: String) =
        path.isEmpty() ||
            path in directories ||
            directories.any { it.startsWith("$path/") } ||
            files.keys.any { it.startsWith("$path/") }

    fun exists(path: String) = path in files || folder(path)

    fun children(path: String): List<ResourceInfo> {
        require(folder(path)) { "Resource folder not found" }
        val prefix = if (path.isEmpty()) "" else "$path/"
        val names =
            (files.keys + directories)
                .filter { it.startsWith(prefix) && it != path }
                .map { it.removePrefix(prefix).substringBefore('/') }
                .distinct()
        return names
            .map { name ->
                val p = prefix + name
                ResourceInfo(
                    p,
                    files[p]?.size
                        ?: files.filterKeys { it.startsWith("$p/") }.values.sumOf { it.size },
                    p !in files,
                )
            }
            .sortedWith(
                compareByDescending<ResourceInfo> { it.directory }.thenBy { it.path.lowercase() }
            )
    }

    fun encode(): Map<String, ByteArray> {
        require(META !in files) {
            "Resource metadata path conflict; rename the existing file first"
        }
        validate()
        val encoded =
            if (directories.isEmpty()) files
            else
                files +
                    (META to
                        JSONObject()
                            .put("format", FORMAT)
                            .put("version", 1)
                            .put("directories", JSONArray(directories.sorted()))
                            .toString()
                            .toByteArray())
        require((encoded[META]?.size ?: 0) <= 2 * 1024 * 1024) {
            "Resource folder metadata exceeds size limit"
        }
        ArchiveFiles.resources(encoded)
        return encoded
    }

    fun addFile(path: String, bytes: ByteArray): ResourceTree {
        newPath(path)
        require(!exists(path)) { "Resource already exists" }
        return copy(files = files + (path to bytes))
    }

    fun addFolder(path: String): ResourceTree {
        newPath(path)
        require(!exists(path)) { "Resource already exists" }
        return copy(directories = directories + path)
    }

    fun remove(paths: List<String>): ResourceTree {
        val roots = roots(paths)
        roots.forEach { require(exists(it)) { "Resource not found" } }
        val parents =
            roots.mapNotNull { it.substringBeforeLast('/', "").takeIf(String::isNotEmpty) }
        return ResourceTree(
            files.filterKeys { p -> roots.none { within(p, it) } },
            (directories + parents).filterTo(linkedSetOf()) { p -> roots.none { within(p, it) } },
        )
    }

    fun renamed(path: String, target: String): ResourceTree {
        ArchiveFiles.path(path)
        newPath(target)
        require(exists(path)) { "Resource not found" }
        if (path == target) return this
        require(!within(target, path)) { "Cannot move resource into itself or descendant" }
        val remaining = remove(listOf(path))
        require(!remaining.exists(target)) { "Resource name conflict" }
        val copied = subtree(path, target)
        return ResourceTree(
                remaining.files + copied.files,
                remaining.directories + copied.directories,
            )
            .also { it.validate() }
    }

    fun paste(
        paths: List<String>,
        target: ResourceTree,
        destination: String,
        sameOwner: Boolean,
        cut: Boolean,
    ): Pair<ResourceTree, ResourceTree> {
        if (destination.isNotEmpty()) newPath(destination)
        val roots = roots(paths)
        roots.forEach { require(exists(it)) { "Resource not found" } }
        if (sameOwner)
            require(roots.none { within(destination, it) }) {
                "Cannot paste into itself or descendant"
            }
        var result = target
        roots.forEach { path ->
            val to =
                (if (destination.isEmpty()) "" else "$destination/") + path.substringAfterLast('/')
            require(!result.exists(to)) { "Resource name conflict" }
            val part = subtree(path, to)
            result = ResourceTree(result.files + part.files, result.directories + part.directories)
            result.validate()
        }
        val source = if (cut) remove(roots) else this
        if (sameOwner && cut)
            result =
                ResourceTree(
                    source.files + (result.files - files.keys),
                    source.directories + (result.directories - directories),
                )
        result.validate()
        return source to result
    }

    private fun subtree(path: String, target: String): ResourceTree =
        ResourceTree(
            files.filterKeys { within(it, path) }.mapKeys { target + it.key.removePrefix(path) },
            directories
                .filter { within(it, path) }
                .mapTo(linkedSetOf()) { target + it.removePrefix(path) } +
                if (folder(path)) setOf(target) else emptySet(),
        )

    private fun validate() {
        ArchiveFiles.tree(files.keys)
        require(directories.size <= 5000) { "Too many resource folders" }
        directories.forEach { dir ->
            newPath(dir)
            require(dir !in files && ancestors(dir).none { it in files }) {
                "Resource file/folder name conflict"
            }
        }
    }

    companion object {
        const val META = ".pocket-resource-tree.v1.json"
        private const val FORMAT = "dev.pocket.notebook.resources"

        fun decode(raw: Map<String, ByteArray>): ResourceTree {
            val record =
                raw[META]
                    ?.takeIf { it.size <= 2 * 1024 * 1024 }
                    ?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }
                    ?.takeIf { it.optString("format") == FORMAT }
            if (record == null) return ResourceTree(raw).also { it.validate() }
            require(record.getInt("version") == 1) { "Unsupported resource metadata version" }
            val array = record.getJSONArray("directories")
            require(array.length() <= 5000)
            return ResourceTree(
                    raw - META,
                    (0 until array.length()).map { array.getString(it) }.toSet(),
                )
                .also { it.validate() }
        }

        fun newPath(path: String) {
            ArchiveFiles.path(path)
            require(path != META && !path.startsWith("$META/")) {
                "Reserved resource metadata path"
            }
        }

        fun roots(paths: List<String>): List<String> {
            require(paths.isNotEmpty())
            paths.forEach(ArchiveFiles::path)
            return paths.distinct().filter { p -> paths.none { it != p && within(p, it) } }
        }

        fun within(path: String, root: String) = path == root || path.startsWith("$root/")

        private fun ancestors(path: String): Sequence<String> =
            generateSequence(path.substringBeforeLast('/', "")) { it.substringBeforeLast('/', "") }
                .takeWhile { it.isNotEmpty() }

        fun detached(
            personal: Map<String, ByteArray>,
            shared: Map<String, ByteArray>,
        ): Map<String, ByteArray> {
            val own = decode(personal)
            val common = decode(shared)
            if (common.files.isEmpty() && common.directories.isEmpty()) return personal
            return ResourceTree(
                    common.files.mapKeys { "shared/${it.key}" } + own.files,
                    common.directories.mapTo(linkedSetOf()) { "shared/$it" } +
                        setOf("shared") +
                        own.directories,
                )
                .encode()
        }
    }
}
