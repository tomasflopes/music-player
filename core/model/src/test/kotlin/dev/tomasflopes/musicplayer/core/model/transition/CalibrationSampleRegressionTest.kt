package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The owner's 40 labelled songs (calibration/sample.csv, ADR 0099): stored tempo confidence, the tempo tap result
 * (y, half, double or n) and whether the click on the stored grid sat on the beats for 30 s near the outro. 8 songs per
 * confidence bucket, mostly hip-hop. Row numbers match the CSV. The rules must keep the counts below, so a change of
 * either threshold or of the signal shows up here before it reaches a phone.
 */
class CalibrationSampleRegressionTest {
    private class SampleSong(
        val row: Int,
        val confidence: Float,
        val tempoOk: String,
        val beatOk: Boolean,
    )

    private val config = TransitionConfig()

    private fun eligible(rule: TempoReliability) =
        sample.filter { rule.isReliable(transitionProfile(confidence = it.confidence), config) }

    @Test
    fun `the stored rule passes 8 of 40 sample songs and all of them are on the beat`() {
        val passed = eligible(StoredConfidenceReliability())

        assertEquals(8, passed.size)
        assertTrue(passed.all { it.beatOk })
    }

    @Test
    fun `the calibrated rule passes 29 of 40 sample songs and 27 of them are on the beat`() {
        val passed = eligible(CalibratedConfidenceReliability())

        assertEquals(29, passed.size)
        assertEquals(27, passed.count { it.beatOk })
        assertTrue(passed.count { it.beatOk } >= 0.9 * passed.size, "precision under the 90 % target")
    }

    @Test
    fun `the calibrated rule never passes a song whose stored tempo is a 3 to 2 peak`() {
        val offGrid = sample.filter { it.tempoOk == "n" }

        assertEquals(listOf(4, 14), offGrid.map { it.row })
        assertTrue(eligible(CalibratedConfidenceReliability()).none { it.tempoOk == "n" })
    }

    @Test
    fun `every song the stored rule passes is also passed by the calibrated rule`() {
        val stored = eligible(StoredConfidenceReliability()).map { it.row }.toSet()
        val calibrated = eligible(CalibratedConfidenceReliability()).map { it.row }.toSet()

        assertTrue(calibrated.containsAll(stored))
    }

    private companion object {
        val sample =
            listOf(
                SampleSong(1, 0.0583f, "y", true),
                SampleSong(2, 0.0f, "y", false),
                SampleSong(3, 0.045f, "y", false),
                SampleSong(4, 0.0041f, "n", false),
                SampleSong(5, 0.079f, "half", true),
                SampleSong(6, 0.0891f, "half", true),
                SampleSong(7, 0.0f, "y", true),
                SampleSong(8, 0.0717f, "y", false),
                SampleSong(9, 0.1577f, "y", false),
                SampleSong(10, 0.1603f, "y", true),
                SampleSong(11, 0.1036f, "y", true),
                SampleSong(12, 0.1651f, "y", true),
                SampleSong(13, 0.191f, "half", true),
                SampleSong(14, 0.1457f, "n", false),
                SampleSong(15, 0.1404f, "y", true),
                SampleSong(16, 0.1641f, "half", true),
                SampleSong(17, 0.3422f, "y", true),
                SampleSong(18, 0.2275f, "y", true),
                SampleSong(19, 0.2433f, "y", true),
                SampleSong(20, 0.2703f, "y", true),
                SampleSong(21, 0.2536f, "half", true),
                SampleSong(22, 0.2427f, "half", true),
                SampleSong(23, 0.3295f, "y", true),
                SampleSong(24, 0.2241f, "half", true),
                SampleSong(25, 0.4248f, "y", true),
                SampleSong(26, 0.4453f, "y", true),
                SampleSong(27, 0.3548f, "y", true),
                SampleSong(28, 0.3725f, "y", true),
                SampleSong(29, 0.387f, "half", true),
                SampleSong(30, 0.3515f, "half", true),
                SampleSong(31, 0.4038f, "y", false),
                SampleSong(32, 0.4429f, "half", true),
                SampleSong(33, 0.5207f, "y", true),
                SampleSong(34, 0.5457f, "y", true),
                SampleSong(35, 0.501f, "y", true),
                SampleSong(36, 0.5458f, "half", true),
                SampleSong(37, 0.5247f, "half", true),
                SampleSong(38, 0.506f, "half", true),
                SampleSong(39, 0.5153f, "half", true),
                SampleSong(40, 0.5358f, "y", true),
            )
    }
}
