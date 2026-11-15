package dev.tomasflopes.musicplayer.ui.settings.analysis

import dev.tomasflopes.musicplayer.data.db.dao.AnalysisProgressRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class AnalysisProgressTextTest {
    private fun text(
        profiled: Int,
        waiting: Int,
        gaveUp: Int = 0,
    ) = AnalysisProgressText.format(AnalysisProgressRow(profiled, waiting, gaveUp), Locale.US)

    @Test
    fun `progress reads analysed over total and the phone queue`() {
        assertEquals("Analysed 14,164 / 14,165, 1 on phone queue", text(profiled = 14_164, waiting = 1))
    }

    @Test
    fun `a long queue says it waits for the charger`() {
        assertEquals(
            "Analysed 0 / 14,165, 14,165 on phone queue (waits for the charger)",
            text(profiled = 0, waiting = 14_165),
        )
    }

    @Test
    fun `songs the phone gave up on are counted apart`() {
        assertEquals("Analysed 9 / 10, 0 on phone queue (1 failed)", text(profiled = 9, waiting = 0, gaveUp = 1))
    }

    @Test
    fun `a finished library has an empty queue`() {
        assertEquals("Analysed 5 / 5, 0 on phone queue", text(profiled = 5, waiting = 0))
    }
}
