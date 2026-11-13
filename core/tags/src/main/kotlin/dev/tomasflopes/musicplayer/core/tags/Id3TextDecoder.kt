package dev.tomasflopes.musicplayer.core.tags

import java.nio.charset.Charset

/** Decodes the text of an ID3v2 text frame: one encoding byte (passed separately), then one or more values. */
internal object Id3TextDecoder {
    private const val UTF16_WITH_BOM = 1
    private const val UTF16_BIG_ENDIAN = 2
    private const val UTF8 = 3
    private const val NUL: Byte = 0
    private const val BOM_SIZE = 2
    private val BOM_LITTLE_ENDIAN = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_BIG_ENDIAN = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /**
     * Values are separated by NUL; trailing NULs are dropped. An unknown [encodingByte] is read as Latin-1,
     * the ID3 default. UTF-16 without a byte order mark is read as little endian (what Windows tools write).
     */
    fun decode(
        encodingByte: Int,
        bytes: ByteArray,
    ): List<String> {
        val values =
            when (encodingByte) {
                UTF16_WITH_BOM -> utf16Values(bytes, Charsets.UTF_16LE)
                UTF16_BIG_ENDIAN -> utf16Values(bytes, Charsets.UTF_16BE)
                UTF8 -> splitOnNul(bytes, unitSize = 1).map { String(it, Charsets.UTF_8) }
                else -> splitOnNul(bytes, unitSize = 1).map { String(it, Charsets.ISO_8859_1) }
            }
        return values.dropLastWhile { it.isEmpty() }
    }

    /** A value with its own byte order mark sets the order for itself and for the values after it. */
    private fun utf16Values(
        bytes: ByteArray,
        initialOrder: Charset,
    ): List<String> {
        var order = initialOrder
        return splitOnNul(bytes, unitSize = 2).map { value ->
            val marked = byteOrderOf(value)
            if (marked != null) order = marked
            val start = if (marked != null) BOM_SIZE else 0
            String(value, start, value.size - start, order)
        }
    }

    private fun byteOrderOf(value: ByteArray): Charset? =
        when {
            value.startsWith(BOM_LITTLE_ENDIAN) -> Charsets.UTF_16LE
            value.startsWith(BOM_BIG_ENDIAN) -> Charsets.UTF_16BE
            else -> null
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    /** Splits on a NUL that is [unitSize] bytes wide and aligned to [unitSize]; a dangling odd byte is ignored. */
    private fun splitOnNul(
        bytes: ByteArray,
        unitSize: Int,
    ): List<ByteArray> {
        val usable = bytes.size - bytes.size % unitSize
        val values = mutableListOf<ByteArray>()
        var valueStart = 0
        for (index in 0 until usable step unitSize) {
            if ((index until index + unitSize).all { bytes[it] == NUL }) {
                values += bytes.copyOfRange(valueStart, index)
                valueStart = index + unitSize
            }
        }
        values += bytes.copyOfRange(valueStart, usable)
        return values
    }
}
