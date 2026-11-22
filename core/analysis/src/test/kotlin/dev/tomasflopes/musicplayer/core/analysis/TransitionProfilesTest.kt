package dev.tomasflopes.musicplayer.core.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TransitionProfilesTest {
    private fun fixed(edit: (FloatArray) -> Unit = {}): FloatArray =
        FloatArray(SoundProfile.FIXED_SIZE).also {
            it[SoundProfile.FIRST_BEAT] = 0.5f
            it[SoundProfile.BEAT_TEMPO] = 120.0f
            it[SoundProfile.TEMPO_CONFIDENCE] = 0.8f
            it[SoundProfile.CANDIDATES] = 120f
            it[SoundProfile.CANDIDATES + 2] = 60f
            it[SoundProfile.MUSIC_START] = 0.4f
            it[SoundProfile.INTRO_END] = 12f
            it[SoundProfile.OUTRO_START] = 200f
            it[SoundProfile.MUSIC_END] = 215f
            edit(it)
        }

    @Test
    fun `reads the grid, the confidence, the candidates and the boundaries in milliseconds`() {
        val profile = TransitionProfiles.fromFixed(fixed())!!

        assertEquals(120.0, profile.tempoBpm, 1e-6)
        assertEquals(500.0, profile.firstBeatMs, 1e-6)
        assertEquals(0.8f, profile.tempoConfidence)
        assertEquals(400L, profile.musicStartMs)
        assertEquals(12_000L, profile.introEndMs)
        assertEquals(200_000L, profile.outroStartMs)
        assertEquals(215_000L, profile.musicEndMs)
        assertEquals(listOf(120.0, 60.0), profile.candidateBpms)
    }

    @Test
    fun `numbers that cannot be a beat grid give no profile`() {
        assertNull(TransitionProfiles.fromFixed(fixed { it[SoundProfile.BEAT_TEMPO] = 0f }))
        assertNull(TransitionProfiles.fromFixed(fixed { it[SoundProfile.BEAT_TEMPO] = Float.NaN }))
        assertNull(TransitionProfiles.fromFixed(fixed { it[SoundProfile.FIRST_BEAT] = -1f }))
        assertNull(TransitionProfiles.fromFixed(fixed { it[SoundProfile.OUTRO_START] = 5f }))
        assertNull(TransitionProfiles.fromFixed(fixed { it[SoundProfile.MUSIC_END] = Float.NaN }))
        assertNotNull(TransitionProfiles.fromFixed(fixed()))
    }
}
