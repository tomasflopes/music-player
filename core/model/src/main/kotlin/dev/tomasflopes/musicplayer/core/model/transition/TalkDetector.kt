package dev.tomasflopes.musicplayer.core.model.transition

/**
 * What the stored energy curve says about the end of a song (ADR 0104): the tail from outro start to music end, and the
 * body from intro end to outro start, each as the mean of the stored per-second RMS values. [endMs] is the music end
 * capped at the song's duration.
 */
data class TalkMeasure(
    val outroStartMs: Long,
    val endMs: Long,
    val bodyMean: Float,
    val tailMean: Float,
) {
    val tailMs: Long get() = endMs - outroStartMs

    /** Tail level over body level; infinite when the body is silent. */
    val ratio: Float get() = if (bodyMean > 0f) tailMean / bodyMean else Float.POSITIVE_INFINITY

    /** The heuristic of design phase 7: a long enough tail that is much quieter than the body counts as talk. */
    fun isTalk(config: TransitionConfig): Boolean =
        tailMs >= config.talkMinMs && bodyMean > 0f && tailMean <= config.talkEnergyRatio * bodyMean
}

/**
 * Profile v1 has no speech detector, so a talking outro is guessed from the stored data (design phase 7, slice 4,
 * ADR 0104). Pure: no player, no clock. A bin of the energy curve belongs to a range when the middle of that second
 * lies in it. Anything missing, out of order or not finite gives null, which means "not talk".
 */
object TalkDetector {
    fun measure(
        profile: TransitionProfile,
        durationMs: Long,
    ): TalkMeasure? {
        val curve = profile.energyPerSecond
        val end = minOf(profile.musicEndMs, durationMs)
        val usable = profile.isUsable && end > profile.outroStartMs && curve.size * MS_PER_BIN >= end - MS_PER_BIN
        val body = if (usable) mean(curve, profile.introEndMs, profile.outroStartMs) else null
        val tail = if (usable) mean(curve, profile.outroStartMs, end) else null
        return if (body == null || tail == null) null else TalkMeasure(profile.outroStartMs, end, body, tail)
    }

    /** The measure when the tail of the song is talk under [config], else null. */
    fun detect(
        profile: TransitionProfile,
        durationMs: Long,
        config: TransitionConfig,
    ): TalkMeasure? = measure(profile, durationMs)?.takeIf { it.isTalk(config) }

    private fun mean(
        curve: List<Float>,
        fromMs: Long,
        toMs: Long,
    ): Float? {
        var sum = 0.0
        var count = 0
        for (i in curve.indices) {
            val middle = i * MS_PER_BIN + MS_PER_BIN / 2
            if (middle in fromMs until toMs) {
                val value = curve[i]
                if (!value.isFinite() || value < 0f) return null
                sum += value
                count++
            }
        }
        return if (count == 0) null else (sum / count).toFloat()
    }

    private const val MS_PER_BIN = 1_000L
}
