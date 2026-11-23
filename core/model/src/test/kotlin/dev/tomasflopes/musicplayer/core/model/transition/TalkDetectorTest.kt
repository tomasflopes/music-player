package dev.tomasflopes.musicplayer.core.model.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The talk heuristic on synthetic energy curves (design phase 7, slice 4, ADR 0104). The default song has an intro to
 * 20 s, a body at 0.2 to 160 s and a tail at 0.04 to 178 s, so the tail is 18 s at 0.2 of the body.
 */
class TalkDetectorTest {
    private val config = TransitionConfig()

    private fun profile(
        tail: Float = 0.04f,
        outroStartMs: Long = 160_000,
        musicEndMs: Long = 178_000,
        introEndMs: Long = 20_000,
        energy: List<Float>? = null,
    ) = transitionProfile(
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        musicEndMs = musicEndMs,
        energy = energy ?: energyCurve(introEndMs, outroStartMs, musicEndMs, tail = tail),
    )

    private fun detect(
        profile: TransitionProfile,
        durationMs: Long = 180_000,
    ) = TalkDetector.detect(profile, durationMs, config)

    @Test
    fun `a quiet long tail is talk`() {
        val talk = detect(profile())

        assertNotNull(talk)
        assertEquals(160_000L, talk!!.outroStartMs)
        assertEquals(178_000L, talk.endMs)
        assertEquals(0.2f, talk.bodyMean, 1e-6f)
        assertEquals(0.04f, talk.tailMean, 1e-6f)
        assertEquals(0.2f, talk.ratio, 1e-5f)
    }

    @Test
    fun `a quiet musical outro is not talk`() {
        assertNull(detect(profile(tail = 0.14f)), "0.7 of the body is a quiet outro, not talk")
        assertNull(detect(profile(tail = 0.2f)), "an outro as loud as the body")
    }

    @Test
    fun `the energy ratio limit is inclusive`() {
        assertNotNull(detect(profile(tail = 0.1f)), "exactly 0.5 of the body")
        assertNull(detect(profile(tail = 0.102f)), "just over 0.5")
    }

    @Test
    fun `a tail shorter than eight seconds is not talk and eight seconds is`() {
        assertNull(detect(profile(outroStartMs = 171_000, musicEndMs = 178_000)), "7 s")
        assertNotNull(detect(profile(outroStartMs = 170_000, musicEndMs = 178_000)), "8 s")
    }

    @Test
    fun `the body is intro end to outro start, so a quiet intro does not lower it`() {
        val measure = TalkDetector.measure(profile(), 180_000)!!

        assertEquals(0.2f, measure.bodyMean, 1e-6f)
    }

    @Test
    fun `a missing or short energy curve means no talk`() {
        assertNull(detect(profile(energy = emptyList())), "no curve")
        assertNull(detect(profile(energy = energyCurve().take(100))), "a curve that stops before the music end")
    }

    @Test
    fun `a curve with a value that is not a level means no talk`() {
        val broken = energyCurve().toMutableList().also { it[170] = Float.NaN }
        val negative = energyCurve().toMutableList().also { it[100] = -0.1f }

        assertNull(detect(profile(energy = broken)))
        assertNull(detect(profile(energy = negative)))
    }

    @Test
    fun `a silent body gives no ratio and no talk`() {
        assertNull(detect(profile(energy = energyCurve(body = 0f, intro = 0f, tail = 0f))))
    }

    @Test
    fun `boundaries out of order or an empty body mean no talk`() {
        assertNull(TalkDetector.measure(profile(outroStartMs = 178_000, musicEndMs = 160_000), 180_000))
        assertNull(TalkDetector.measure(profile(introEndMs = 160_000), 180_000), "no body")
    }

    @Test
    fun `the music end is capped at the song duration`() {
        assertNull(detect(profile(), durationMs = 165_000), "5 s of tail left")
        assertEquals(170_000L, detect(profile(), durationMs = 170_000)!!.endMs)
    }

    @Test
    fun `a measure reports the ratio and the length it judged`() {
        val measure = TalkDetector.measure(profile(tail = 0.14f), 180_000)!!

        assertEquals(18_000L, measure.tailMs)
        assertEquals(0.7f, measure.ratio, 1e-5f)
        assertFalse(measure.isTalk(config))
        assertTrue(measure.isTalk(config.copy(talkEnergyRatio = 0.8f)))
    }
}
