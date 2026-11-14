package dev.tomasflopes.musicplayer.core.taste.score

/** How one log row ended, as far as taste is concerned. */
enum class PlayOutcome {
    COMPLETED,
    SKIPPED_EARLY,
    SKIPPED_MID,
    SKIPPED_LATE,

    /** Neither a verdict for nor against: stopped before 80 %, for example when the app closed. */
    NEUTRAL,
}
