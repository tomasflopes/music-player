package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsistencyReportTest {
    private fun profile(vararg values: Pair<Int, Float>): SoundProfile {
        val fixed = FloatArray(SoundProfile.FIXED_SIZE) { 1f }
        values.forEach { (index, value) -> fixed[index] = value }
        return SoundProfile(fixed, FloatArray(0))
    }

    @Test
    fun `tempo is compared in bpm and the rest as a share of the stored value`() {
        val stored = profile(SoundProfile.TEMPO to 120f, SoundProfile.MEAN_LOUDNESS to 0.1f)
        val fresh = profile(SoundProfile.TEMPO to 120.4f, SoundProfile.MEAN_LOUDNESS to 0.103f)

        val gaps = ConsistencyReport.compare(listOf(stored to fresh)).associateBy { it.name }

        assertEquals(0.4, gaps.getValue("tempo_bpm").maxGap, 1e-3)
        assertEquals(0, gaps.getValue("tempo_bpm").songsOver)
        assertEquals(0.03, gaps.getValue("mean_loudness").maxGap, 1e-3)
        assertEquals(1, gaps.getValue("mean_loudness").songsOver)
    }

    @Test
    fun `the largest gap over all songs is the one reported`() {
        val stored = profile(SoundProfile.TEMPO to 100f)
        val close = profile(SoundProfile.TEMPO to 100.2f)
        val far = profile(SoundProfile.TEMPO to 101.5f)

        val tempo = ConsistencyReport.compare(listOf(stored to close, stored to far)).first()

        assertEquals(1.5, tempo.maxGap, 1e-3)
        assertEquals(1, tempo.songsOver)
    }

    @Test
    fun `onset based features get 5 percent and the others stay at 2 percent`() {
        val stored = profile(SoundProfile.ONSET_DENSITY to 2f, SoundProfile.BEAT_STRENGTH to 2f)
        val fresh = profile(SoundProfile.ONSET_DENSITY to 2.1f, SoundProfile.BEAT_STRENGTH to 2.14f)

        val gaps = ConsistencyReport.compare(listOf(stored to fresh)).associateBy { it.name }

        assertEquals(0.05, gaps.getValue("onset_density").limit, 1e-9)
        assertEquals(0, gaps.getValue("onset_density").songsOver)
        assertEquals(1, gaps.getValue("beat_strength").songsOver)
        assertEquals(0.02, gaps.getValue("bass_energy_ratio").limit, 1e-9)
    }

    @Test
    fun `the text has one line per feature and names the song count`() {
        val same = profile()

        val text = ConsistencyReport.format(ConsistencyReport.compare(listOf(same to same)), songs = 1)

        assertEquals(1 + SoundProfile.FEATURE_COUNT, text.lines().size)
        assertTrue(text.contains("1 songs") && text.contains("tempo_bpm") && text.contains("zero_crossing_rate"))
    }
}
