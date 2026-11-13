package dev.tomasflopes.musicplayer.core.tags

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** A square, solid orange PNG written by hand, so sample covers need no image file and no image library. */
internal object OrangePng {
    private const val DEFAULT_SIDE = 64
    private const val CHANNELS = 3
    private const val BIT_DEPTH: Byte = 8
    private const val COLOUR_TYPE_RGB: Byte = 2
    private const val NO_FILTER: Byte = 0
    private val ORANGE = byteArrayOf(0xFF.toByte(), 0x8C.toByte(), 0x00)
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun bytes(side: Int = DEFAULT_SIDE): ByteArray {
        val row = byteArrayOf(NO_FILTER) + ByteArray(side * CHANNELS) { ORANGE[it % CHANNELS] }
        val pixels = ByteArray(row.size * side) { row[it % row.size] }
        val header = int(side) + int(side) + byteArrayOf(BIT_DEPTH, COLOUR_TYPE_RGB, 0, 0, 0)
        return SIGNATURE + chunk("IHDR", header) + chunk("IDAT", zlib(pixels)) + chunk("IEND", ByteArray(0))
    }

    private fun chunk(
        type: String,
        data: ByteArray,
    ): ByteArray {
        val typeAndData = type.toByteArray(Charsets.US_ASCII) + data
        val checksum = CRC32().apply { update(typeAndData) }.value.toInt()
        return int(data.size) + typeAndData + int(checksum)
    }

    private fun zlib(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun int(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}
