package dev.tomasflopes.musicplayer.core.analysis.contract

import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import kotlin.math.abs
import kotlin.math.max

/**
 * Compares a profile with its golden values (ADR 0060, 0065), shared by the JVM test in `check` and the MediaCodec
 * test in `deviceCheck`. The 8 v1 features keep the ADR 0060 tolerances (tempo ±1 BPM, the rest ±2 % relative); the
 * transition data has looser, absolute ones until a golden run on a phone says what a decoder difference costs.
 * Beat measures are only compared when the golden file itself heard a beat (tempo confidence at least 0.1): a held
 * tone or noise has no tempo to agree on.
 */
object GoldenCompare {
    private const val TEMPO_BPM = 1f
    private const val RELATIVE = 0.02f
    private const val FLOOR = 1e-4f
    private const val BEAT_SECONDS = 0.06f
    private const val BOUNDARY_SECONDS = 0.15f
    private const val SHARE = 0.03f
    private const val MIN_CONFIDENCE = 0.1f
    private const val CURVE = 0.02f

    fun differences(
        expected: SoundProfile,
        actual: SoundProfile,
    ): List<String> {
        val found = mutableListOf<String>()
        val beat = expected.tempoConfidence >= MIN_CONFIDENCE

        fun check(
            index: Int,
            tolerance: (Float) -> Float,
        ) {
            val e = expected.fixed[index]
            val a = actual.fixed[index]
            if (abs(e - a) > tolerance(e)) found += "${SoundProfile.FIXED_NAMES[index]}: expected $e, got $a"
        }
        val relative = { e: Float -> max(FLOOR, RELATIVE * abs(e)) }
        for (index in listOf(
            SoundProfile.MEAN_LOUDNESS,
            SoundProfile.LOUDNESS_VARIATION,
            SoundProfile.SPECTRAL_CENTROID,
        )) {
            check(index, relative)
        }
        check(SoundProfile.BASS_RATIO, relative)
        check(SoundProfile.ZERO_CROSSING_RATE, relative)
        if (beat) {
            check(SoundProfile.TEMPO) { TEMPO_BPM }
            check(SoundProfile.BEAT_TEMPO) { TEMPO_BPM }
            check(SoundProfile.ONSET_DENSITY, relative)
            check(SoundProfile.FIRST_BEAT) { BEAT_SECONDS }
        }
        for (index in SoundProfile.CHROMA_FIRST until SoundProfile.CHROMA_FIRST + SoundProfile.CHROMA_BINS) {
            check(
                index,
            ) {
                SHARE
            }
        }
        for (index in SoundProfile.MUSIC_START..SoundProfile.MUSIC_END) check(index) { BOUNDARY_SECONDS }
        found += curveDifferences(expected.energyCurve, actual.energyCurve)
        return found
    }

    private fun curveDifferences(
        expected: FloatArray,
        actual: FloatArray,
    ): List<String> {
        if (expected.size !=
            actual.size
        ) {
            return listOf("energy curve: expected ${expected.size} values, got ${actual.size}")
        }
        return expected.indices
            .filter { abs(expected[it] - actual[it]) > max(CURVE * abs(expected[it]), FLOOR) }
            .map { "energy[$it]: expected ${expected[it]}, got ${actual[it]}" }
    }
}
