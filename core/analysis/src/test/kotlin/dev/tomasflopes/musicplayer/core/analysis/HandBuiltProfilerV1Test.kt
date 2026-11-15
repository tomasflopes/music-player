package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.TestSignals.clicks
import dev.tomasflopes.musicplayer.core.analysis.TestSignals.concat
import dev.tomasflopes.musicplayer.core.analysis.TestSignals.profile
import dev.tomasflopes.musicplayer.core.analysis.TestSignals.silence
import dev.tomasflopes.musicplayer.core.analysis.TestSignals.sine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.math.sqrt

class HandBuiltProfilerV1Test {
    @Test
    fun `a 440 Hz sine has its centroid at 440 and the level of its amplitude`() {
        val p = profile(sine(440.0, 6.0))

        assertEquals(440f, p.spectralCentroidHz, 12f)
        assertEquals(0.5f / sqrt(2f), p.meanLoudness, 0.01f)
        assertTrue(p.loudnessVariation < 0.01f)
        assertEquals(2 * 440f / RATE, p.zeroCrossingRate, 0.004f)
        assertEquals(6, p.energyCurve.size)
        p.energyCurve.forEach { assertEquals(0.5f / sqrt(2f), it, 0.01f) }
    }

    @Test
    fun `a held tone and noise do not claim a confident beat`() {
        val tone = profile(sine(440.0, 8.0))

        assertTrue(tone.tempoConfidence < 0.2f, "confidence ${tone.tempoConfidence}")
        assertTrue(tone.beatStrength < 0.3f, "strength ${tone.beatStrength}")
    }

    @Test
    fun `80 Hz is bass and 2 kHz is not`() {
        assertTrue(profile(sine(80.0, 4.0)).bassEnergyRatio > 0.9f)
        assertTrue(profile(sine(2_000.0, 4.0)).bassEnergyRatio < 0.02f)
    }

    @ParameterizedTest
    @ValueSource(doubles = [90.0, 120.0, 150.0])
    fun `a click track gives its tempo and the time of its first click`(bpm: Double) {
        val p = profile(clicks(bpm, seconds = 20.0, offsetSeconds = 0.3))

        assertEquals(bpm.toFloat(), p.tempoBpm, 1f)
        assertEquals(p.tempoBpm, p.beatTempoBpm)
        assertEquals(0.3f, p.firstBeatSeconds, 0.06f)
        assertTrue(p.tempoConfidence > 0f)
        assertTrue(p.beatStrength > 0.2f, "beat strength ${p.beatStrength}")
        assertTrue(p.onsetDensity in 0.6f * bpm.toFloat() / 60f..1.4f * bpm.toFloat() / 60f, "onsets ${p.onsetDensity}")
        assertEquals(p.tempoBpm, p.candidate(0).first, 1f + 0.5f * p.tempoBpm)
        assertEquals(1f, p.candidate(0).second)
    }

    @Test
    fun `tempo is folded into 60 to 180 and the raw peak stays a candidate`() {
        val p = profile(clicks(200.0, seconds = 20.0))

        assertEquals(100f, p.tempoBpm, 1.5f)
        assertTrue(
            (0 until 3).any {
                abs(p.candidate(it).first - 200f) < 3f
            },
            "candidates: ${(0 until 3).map { p.candidate(it) }}",
        )
    }

    @Test
    fun `a C major chord has C E and G as its strongest pitch classes`() {
        val p = profile(TestSignals.sum(5.0, RATE, 523.25, 659.26, 783.99))

        val strongest =
            p.chroma
                .withIndex()
                .sortedByDescending { it.value }
                .take(3)
                .map { it.index }
                .toSet()
        assertEquals(setOf(0, 4, 7), strongest)
        assertEquals(1f, p.chroma.sum(), 0.001f)
    }

    @Test
    fun `silence gives zeros and no NaN`() {
        val p = profile(silence(5.0))

        assertTrue(p.fixed.all { it == 0f })
        assertEquals(List(5) { 0f }, p.energyCurve.toList())
    }

    @Test
    fun `a track shorter than one frame gives zeros`() {
        val p = profile(sine(440.0, 0.01))

        assertTrue(p.fixed.all { it.isFinite() })
        assertEquals(0f, p.tempoBpm)
    }

    @Test
    fun `silent intro and outro are cut off`() {
        val p = profile(concat(silence(3.0), sine(440.0, 10.0), silence(3.0)))

        assertEquals(3f, p.musicStartSeconds, 0.3f)
        assertEquals(3.2f, p.introEndSeconds, 0.5f)
        assertEquals(12.8f, p.outroStartSeconds, 0.5f)
        assertEquals(13f, p.musicEndSeconds, 0.3f)
        assertTrue(p.musicStartSeconds <= p.introEndSeconds && p.introEndSeconds <= p.outroStartSeconds)
        assertTrue(p.outroStartSeconds <= p.musicEndSeconds)
        assertEquals(16, p.energyCurve.size)
        assertEquals(0f, p.energyCurve[0])
        assertTrue(p.energyCurve[8] > 0.3f)
    }

    @Test
    fun `the first beat is not before the music starts`() {
        val p = profile(concat(silence(4.0), clicks(120.0, seconds = 16.0, offsetSeconds = 0.1)))

        assertTrue(p.firstBeatSeconds >= p.musicStartSeconds - 0.05f, "${p.firstBeatSeconds} vs ${p.musicStartSeconds}")
    }

    @Test
    fun `the same input twice gives the same bits whatever the chunking`() {
        val samples = clicks(110.0, seconds = 12.0, offsetSeconds = 0.2)
        val once = profile(samples)

        val stream = HandBuiltProfilerV1().newStream(RATE)
        var from = 0
        while (from < samples.size) {
            val count = minOf(777, samples.size - from)
            stream.feed(samples.copyOfRange(from, from + count), count)
            from += count
        }

        assertEquals(once, profile(samples))
        assertEquals(once, stream.finish())
    }

    @ParameterizedTest
    @ValueSource(ints = [44_100, 48_000, 32_000])
    fun `other sample rates are resampled to the same profile within a small gap`(rate: Int) {
        val p = profile(sine(440.0, 5.0, rate), rate)

        assertEquals(440f, p.spectralCentroidHz, 12f)
        assertEquals(0.5f / sqrt(2f), p.meanLoudness, 0.01f)
        assertEquals(5, p.energyCurve.size)
    }
}
