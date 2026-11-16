package dev.pocket.notebook.data.packing

import dev.pocket.notebook.data.ResourceTree
import java.io.*
import java.util.zip.*

/** Shared limits for user-controlled archives, including decompression bombs. */
object ArchiveFiles {
    const val MAX_TOTAL = 128 * 1024 * 1024
    const val MAX_FILE = 64 * 1024 * 1024
    const val MAX_FILES = 5000
    const val MAX_RESOURCES = 2000
    const val MAX_RESOURCE_BYTES = 48L * 1024 * 1024

    fun path(name: String): String {
        require(
            name.length in 1..1024 &&
                !name.startsWith('/') &&
                '\\' !in name &&
                ':' !in name &&
                name.none(Char::isISOControl)
        ) {
            "Unsafe resource path"
        }
        require(
            name.split('/').all { it.isNotBlank() && it !in setOf(".", "..") && it.length <= 120 }
        ) {
            "Unsafe resource path"
        }
        return name
    }

    fun resources(files: Map<String, ByteArray>) {
        require(
            files.size <= MAX_RESOURCES &&
                files.values.sumOf { it.size.toLong() } <= MAX_RESOURCE_BYTES
        )
        tree(files.keys)
        dev.pocket.notebook.data.ResourceTree.decode(files)
    }

    fun tree(names: Set<String>) {
        names.forEach {
            path(it)
            var p = it
            while (p.contains('/')) {
                p = p.substringBeforeLast('/')
                require(p !in names) { "Resource file/folder name conflict" }
            }
        }
    }

    fun read(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            require(out.size().toLong() + n <= limit) { "File exceeds size limit" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun pack(files: Map<String, ByteArray>): ByteArray {
        require(files.size in 1..MAX_FILES && files.values.sumOf { it.size.toLong() } <= MAX_TOTAL)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, bytes) ->
                path(name)
                require(bytes.size <= MAX_FILE)
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray().also { require(it.size <= MAX_TOTAL) }
    }

    fun unpack(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size <= MAX_TOTAL)
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                require(!e.isDirectory && files.size < MAX_FILES)
                path(e.name)
                require(e.name !in files) { "Duplicate archive entry" }
                val data = read(zip, minOf(MAX_FILE, MAX_TOTAL - total))
                total += data.size
                files[e.name] = data
                zip.closeEntry()
            }
        }
        require(files.isNotEmpty())
        return files
    }
}
