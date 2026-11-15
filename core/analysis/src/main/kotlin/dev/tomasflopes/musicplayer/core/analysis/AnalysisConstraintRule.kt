package dev.tomasflopes.musicplayer.core.analysis

/**
 * When the phone analysis may run (ADR 0059). A few songs are cheap and need nothing; a long queue (no import yet,
 * or a big new batch) is hours of work and waits for the charger. Pure, so the rule is tested as a function.
 */
object AnalysisConstraintRule {
    /** Up to this many songs waiting: no constraints. */
    const val FREE_UP_TO = 100

    /** One run of the worker stops after about this long, then the job enqueues itself again. */
    const val BATCH_MINUTES = 9L

    fun requiresCharging(waiting: Int): Boolean = waiting > FREE_UP_TO
}

/** Asks for the phone analysis to run. Called after every scan and every import (ADR 0059). */
interface AnalysisScheduler {
    /** Starts the unique job when songs are waiting; does nothing when none are. Safe to call at any time. */
    suspend fun request()
}
