package dev.pocket.notebook.data

/** Shared by URI import and package conversion; MIME fallback preserves existing WebP behavior. */
object CoverImages {
    const val MAX_BYTES = dev.pocket.notebook.data.packing.NotebookPackage.MAX_COVER

    fun mime(bytes: ByteArray): String =
        when {
            bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            else -> "image/webp"
        }
}
