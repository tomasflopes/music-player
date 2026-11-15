package dev.tomasflopes.musicplayer.ui.settings.analysis

import dev.tomasflopes.musicplayer.core.analysis.AnalysisConstraintRule
import dev.tomasflopes.musicplayer.data.db.dao.AnalysisProgressRow
import java.util.Locale

/** The wording of the Analysis row in Settings (phase 3, ADR 0059). Pure, so it is tested without a screen. */
object AnalysisProgressText {
    /** "Analysed 16,102 / 16,302, 200 on phone queue", with the charger and given-up notes when they apply. */
    fun format(
        progress: AnalysisProgressRow,
        locale: Locale = Locale.getDefault(),
    ): String {
        val total = progress.profiled + progress.waiting + progress.gaveUp
        val line =
            "Analysed ${number(progress.profiled, locale)} / ${number(total, locale)}, " +
                "${number(progress.waiting, locale)} on phone queue"
        val notes =
            listOfNotNull(
                "waits for the charger".takeIf { AnalysisConstraintRule.requiresCharging(progress.waiting) },
                "${number(progress.gaveUp, locale)} failed".takeIf { progress.gaveUp > 0 },
            )
        return if (notes.isEmpty()) line else "$line (${notes.joinToString(", ")})"
    }

    private fun number(
        value: Int,
        locale: Locale,
    ) = String.format(locale, "%,d", value)
}
