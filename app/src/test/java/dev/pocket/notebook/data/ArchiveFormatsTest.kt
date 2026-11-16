package dev.pocket.notebook.data

import dev.pocket.notebook.data.packing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArchiveFormatsTest {
    @Test
    fun freshNoncesAndAuthenticatedHeadersRejectAlterationAndWrongPurpose() {
        val key = ByteArray(32) { it.toByte() }
        val salt = ByteArray(16) { it.toByte() }
        val plain = "private contents".toByteArray()
        val a = PortableBundle.seal(plain, key, salt, "collection")
        val b = PortableBundle.seal(plain, key, salt, "collection")
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, PortableBundle.open(a, key, "collection"))
        assertThrows(Exception::class.java) { PortableBundle.open(a, ByteArray(32), "collection") }
        assertThrows(Exception::class.java) { PortableBundle.open(a, key, "library") }
        val altered = a.copyOf()
        altered[altered.lastIndex] = (altered.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { PortableBundle.open(altered, key, "collection") }
        val header = PortableBundle.header(a, "collection")
        val changed =
            header.bytes.toString(Charsets.UTF_8).replace("600000", "600001").toByteArray()
        val aad = a.copyOfRange(0, 8) + changed + a.copyOfRange(header.offset, a.size)
        assertThrows(Exception::class.java) { PortableBundle.open(aad, key, "collection") }
    }

    @Test
    fun traversalDuplicateEntriesAndBoundedReadsAreRejected() {
        val zip = ArchiveFiles.pack(mapOf("a.txt" to byteArrayOf(1), "b.txt" to byteArrayOf(2)))
        val duplicate = zip.copyOf()
        val from = "b.txt".toByteArray()
        val to = "a.txt".toByteArray()
        for (i in 0..duplicate.size - from.size) if (
            duplicate.copyOfRange(i, i + from.size).contentEquals(from)
        )
            to.copyInto(duplicate, i)
        assertThrows(IllegalArgumentException::class.java) { ArchiveFiles.unpack(duplicate) }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveFiles.pack(mapOf("../escape" to byteArrayOf(1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveFiles.read(byteArrayOf(1, 2).inputStream(), 1)
        }
    }

    @Test
    fun oversizedCollectionsAndInconsistentPackagesCannotBeWritten() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBundle.encode(
                PortableBundle.Collection((0..1000).associate { "d$it" to byteArrayOf(1) })
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBundle.encode(
                PortableBundle.Collection(
                    resources = (0..2000).associate { "r$it.txt" to byteArrayOf(1) }
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NotebookPackage.pack(
                "<notebook><title>A</title></notebook>",
                NotebookMetadata("B", "", "", ""),
                null,
                null,
            )
        }
    }
}
