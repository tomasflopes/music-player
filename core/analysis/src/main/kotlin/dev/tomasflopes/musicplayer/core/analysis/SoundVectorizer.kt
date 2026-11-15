package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.SoundVectorizerConfig
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import kotlin.math.floor
import kotlin.math.sqrt

/** One song's stored fixed part (the 33 floats of ADR 0065) and the id it goes by. */
class RawSoundProfile(
    val songId: Long,
    val fixed: FloatArray,
) {
    init {
        require(fixed.size == SoundProfile.FIXED_SIZE) {
            "A profile has ${SoundProfile.FIXED_SIZE} fixed values, not ${fixed.size}"
        }
    }
}

/**
 * Turns profiles into the points Discover measures distance on (ADR 0068). Pure Kotlin, one pass over the library.
 *
 * Five features, in this order: spectral centroid, bass energy ratio, onset density, beat strength, and relative
 * dynamics (loudness variation over mean loudness). Tempo, mean loudness, zero-crossing rate, chroma and the energy
 * curve are not used. Each feature is clipped to its p5..p95 over all the songs given and scaled to 0..1 (percentiles
 * interpolate linearly, numpy's default); beat strength then gets a square root; then each is multiplied by its
 * weight. Normalising over the songs given means the caller decides which songs count: pass the profiled in-library
 * songs and nothing else.
 */
class SoundVectorizer(
    private val config: SoundVectorizerConfig = SoundVectorizerConfig(),
) {
    /** Songs with a non finite feature are left out. Rows come in ascending song id. */
    fun vectorize(profiles: List<RawSoundProfile>): SoundSpace {
        val rows =
            profiles
                .mapNotNull { profile -> raw(profile.fixed)?.let { profile.songId to it } }
                .sortedBy { it.first }
        val columns = Array(DIM) { d -> DoubleArray(rows.size) { rows[it].second[d] } }
        val weights = config.weights()
        val values = FloatArray(rows.size * DIM)
        for (d in 0 until DIM) {
            val low = percentile(columns[d], config.lowPercentile)
            val high = percentile(columns[d], config.highPercentile)
            for (row in rows.indices) {
                var unit = scale(columns[d][row], low, high)
                if (d == BEAT_STRENGTH_DIM) unit = sqrt(unit)
                values[row * DIM + d] = (unit * weights[d]).toFloat()
            }
        }
        return SoundSpace(LongArray(rows.size) { rows[it].first }, DIM, values)
    }

    private fun raw(fixed: FloatArray): DoubleArray? {
        val mean = fixed[SoundProfile.MEAN_LOUDNESS].toDouble()
        val dynamics = if (mean > 0.0) fixed[SoundProfile.LOUDNESS_VARIATION] / mean else 0.0
        val row =
            doubleArrayOf(
                fixed[SoundProfile.SPECTRAL_CENTROID].toDouble(),
                fixed[SoundProfile.BASS_RATIO].toDouble(),
                fixed[SoundProfile.ONSET_DENSITY].toDouble(),
                fixed[SoundProfile.BEAT_STRENGTH].toDouble(),
                dynamics,
            )
        return row.takeIf { r -> r.all { it.isFinite() } }
    }

    private fun scale(
        value: Double,
        low: Double,
        high: Double,
    ): Double = if (high > low) ((value - low) / (high - low)).coerceIn(0.0, 1.0) else 0.0

    /** numpy's default: linear interpolation between the closest ranks of the sorted values. */
    private fun percentile(
        values: DoubleArray,
        percent: Double,
    ): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sortedArray()
        val position = percent / PERCENT * (sorted.size - 1)
        val lower = floor(position).toInt()
        val upper = minOf(lower + 1, sorted.size - 1)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }

    companion object {
        /** Spectral centroid, bass, onset density, beat strength, relative dynamics. */
        const val DIM = 5
        const val BEAT_STRENGTH_DIM = 3
        private const val PERCENT = 100.0

        /** Display names of the dimensions, in vector order (the debug screen and the analyser print them). */
        val NAMES: List<String> = listOf("centroid", "bass", "onset", "beat", "dynamics")
    }
}
