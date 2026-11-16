package dev.pocket.notebook.data

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Lossless supported text formats. The editor uses LF internally; save preserves the file encoding.
 */
internal data class ResourceText(
    val text: String,
    val charset: Charset,
    val bom: ByteArray,
    val newline: String,
) {
    fun encode(value: String): ByteArray {
        val normalized = value.replace("\r\n", "\n").replace("\r", "\n")
        val bytes = bom + normalized.replace("\n", newline).toByteArray(charset)
        require(bytes.size <= MAX_BYTES) { "Resource text exceeds editor limit" }
        return bytes
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024

        fun decode(bytes: ByteArray): ResourceText? {
            if (bytes.size > MAX_BYTES) return null
            val (charset, bom) =
                when {
                    bytes.size >= 3 &&
                        bytes.take(3).map { it.toInt() and 255 } == listOf(239, 187, 191) ->
                        Charsets.UTF_8 to bytes.copyOfRange(0, 3)
                    bytes.size >= 2 && bytes[0] == (-1).toByte() && bytes[1] == (-2).toByte() ->
                        Charsets.UTF_16LE to bytes.copyOfRange(0, 2)
                    bytes.size >= 2 && bytes[0] == (-2).toByte() && bytes[1] == (-1).toByte() ->
                        Charsets.UTF_16BE to bytes.copyOfRange(0, 2)
                    else -> Charsets.UTF_8 to byteArrayOf()
                }
            val raw =
                runCatching {
                        charset
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, bom.size, bytes.size - bom.size))
                            .toString()
                    }
                    .getOrNull() ?: return null
            if (raw.any { it == '\u0000' || (it.isISOControl() && it !in "\r\n\t\u000c") })
                return null
            val newline =
                when {
                    raw.contains("\r\n") -> "\r\n"
                    raw.contains('\r') -> "\r"
                    else -> "\n"
                }
            return ResourceText(
                raw.replace("\r\n", "\n").replace("\r", "\n"),
                charset,
                bom,
                newline,
            )
        }
    }
}
