package dev.tomasflopes.musicplayer.core.analysis

import kotlinx.coroutines.flow.Flow

/** Remembers that the owner closed the import hint (core design 7.0 step 4). */
interface AnalysisHintStore {
    val dismissed: Flow<Boolean>

    suspend fun dismiss()
}

/**
 * The optional hint "Have an analysis file from your computer? Import it in Settings → Analysis". It is only useful
 * while nothing is analysed yet and the phone queue is long (the charging-only case of ADR 0059), and it stays away
 * once closed.
 */
object AnalysisHintRule {
    fun shouldShow(
        profiled: Int,
        waiting: Int,
        dismissed: Boolean,
    ): Boolean = !dismissed && profiled == 0 && AnalysisConstraintRule.requiresCharging(waiting)
}
