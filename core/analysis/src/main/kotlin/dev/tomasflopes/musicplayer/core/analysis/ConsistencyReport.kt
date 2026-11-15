package dev.tomasflopes.musicplayer.core.analysis

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** The largest gap on one v1 feature across the songs compared, and how many songs were over the tolerance. */
data class FeatureGap(
    val name: String,
    val maxGap: Double,
    val limit: Double,
    val isRelative: Boolean,
    val songsOver: Int,
)

/**
 * The debug "Consistency report" (ADR 0060): how far a fresh phone analysis is from the profile the Mac made, per v1
 * feature. Tempo is compared in BPM (limit 1), the others as a share of the stored value (limit 2 %).
 */
object ConsistencyReport {
    private const val TEMPO_LIMIT_BPM = 1.0
    private const val RELATIVE_LIMIT = 0.02
    private const val FLOOR = 1e-4
    private const val PERCENT = 100

    /** Each pair is (stored profile, fresh profile) of one song. */
    fun compare(pairs: List<Pair<SoundProfile, SoundProfile>>): List<FeatureGap> =
        (0 until SoundProfile.FEATURE_COUNT).map { index ->
            val isTempo = index == SoundProfile.TEMPO
            val gaps = pairs.map { (stored, fresh) -> gap(stored.fixed[index], fresh.fixed[index], isTempo) }
            val limit = if (isTempo) TEMPO_LIMIT_BPM else RELATIVE_LIMIT
            FeatureGap(
                name = SoundProfile.FIXED_NAMES[index],
                maxGap = gaps.maxOrNull() ?: 0.0,
                limit = limit,
                isRelative = !isTempo,
                songsOver = gaps.count { it > limit },
            )
        }

    fun format(
        gaps: List<FeatureGap>,
        songs: Int,
    ): String {
        val lines =
            gaps.map { gap ->
                val value = if (gap.isRelative) pct(gap.maxGap) else "%.2f BPM".format(Locale.ROOT, gap.maxGap)
                val limit = if (gap.isRelative) pct(gap.limit) else "%.1f BPM".format(Locale.ROOT, gap.limit)
                val over = "${gap.songsOver} of $songs over"
                "%-22s max gap %-10s limit %-8s %s".format(Locale.ROOT, gap.name, value, limit, over)
            }
        return (listOf("Consistency report: $songs songs, phone against the stored profile") + lines).joinToString("\n")
    }

    private fun pct(share: Double) = "%.2f %%".format(Locale.ROOT, share * PERCENT)

    private fun gap(
        stored: Float,
        fresh: Float,
        absolute: Boolean,
    ): Double {
        val difference = abs(stored.toDouble() - fresh.toDouble())
        return if (absolute) difference else difference / max(FLOOR, abs(stored.toDouble()))
    }
}
