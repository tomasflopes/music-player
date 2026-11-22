package dev.tomasflopes.musicplayer.core.model.crossfade

/** How a transition is made when crossfade is on (phase 7, ADR 0092). */
enum class TransitionStyle {
    /** Exactly phase 6: an equal-power fade of the chosen length. The default. */
    CROSSFADE,

    /** Beat-matched transitions chosen per pair, with the crossfade as the fallback. */
    DJ_MIX,
}

/**
 * The owner's crossfade choice (phase 6): a switch and a length, plus the style of phase 7. Off by default; the length
 * is kept even while the switch is off, so turning it back on restores it.
 */
data class CrossfadeSettings(
    val enabled: Boolean = false,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val style: TransitionStyle = TransitionStyle.CROSSFADE,
) {
    /** The same settings with the length forced into [DURATION_RANGE_MS]. */
    fun coerced() = copy(durationMs = durationMs.coerceIn(DURATION_RANGE_MS))

    companion object {
        const val DEFAULT_DURATION_MS = 4_000L

        /** One to twelve seconds. */
        val DURATION_RANGE_MS = 1_000L..12_000L
    }
}
