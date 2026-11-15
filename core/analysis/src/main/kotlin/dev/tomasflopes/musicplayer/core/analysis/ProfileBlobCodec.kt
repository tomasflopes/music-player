package dev.tomasflopes.musicplayer.core.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val FLOAT_BYTES = 4

/** The BLOB of ADR 0065: raw float32 little-endian, the fixed part then the energy curve; its length is implied. */
object ProfileBlobCodec {
    fun encode(profile: SoundProfile): ByteArray {
        val buffer =
            ByteBuffer.allocate((SoundProfile.FIXED_SIZE + profile.energyCurve.size) * FLOAT_BYTES).order(ORDER)
        profile.fixed.forEach { buffer.putFloat(it) }
        profile.energyCurve.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /** Throws [IllegalArgumentException] for bytes that cannot be a profile. */
    fun decode(bytes: ByteArray): SoundProfile {
        require(bytes.size % FLOAT_BYTES == 0 && bytes.size >= SoundProfile.FIXED_SIZE * FLOAT_BYTES) {
            "Not a sound profile: ${bytes.size} bytes"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ORDER)
        val fixed = FloatArray(SoundProfile.FIXED_SIZE) { buffer.getFloat() }
        val curve = FloatArray(buffer.remaining() / FLOAT_BYTES) { buffer.getFloat() }
        return SoundProfile(fixed, curve)
    }

    /**
     * Only the 33 fixed floats, from a whole blob or from its first 132 bytes (what the sound space reads, ADR 0069).
     * Throws [IllegalArgumentException] for fewer than 132 bytes.
     */
    fun decodeFixed(bytes: ByteArray): FloatArray {
        require(bytes.size >= SoundProfile.FIXED_SIZE * FLOAT_BYTES) { "Not a sound profile head: ${bytes.size} bytes" }
        val buffer = ByteBuffer.wrap(bytes).order(ORDER)
        return FloatArray(SoundProfile.FIXED_SIZE) { buffer.getFloat() }
    }

    private val ORDER = ByteOrder.LITTLE_ENDIAN
}
