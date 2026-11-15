package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.PI
import kotlin.math.sin

internal const val RATE = 22_050

/** Synthetic mono signals for the profiler tests; nothing here touches a file. */
internal object TestSignals {
    fun sum(
        seconds: Double,
        rate: Int = RATE,
        vararg hz: Double,
    ): FloatArray =
        FloatArray((seconds * rate).toInt()) { n ->
            hz.sumOf { sin(2.0 * PI * it * n / rate) }.toFloat() * AMPLITUDE / hz.size
        }

    fun sine(
        hz: Double,
        seconds: Double,
        rate: Int = RATE,
    ): FloatArray = sum(seconds, rate, hz)

    /** 5 ms bursts of a 1 kHz sine at [bpm], the first one at [offsetSeconds] (like `scripts/gen-test-mp3s.sh`). */
    fun clicks(
        bpm: Double,
        seconds: Double,
        offsetSeconds: Double = 0.0,
        rate: Int = RATE,
    ): FloatArray {
        val out = FloatArray((seconds * rate).toInt())
        val period = 60.0 / bpm
        var time = offsetSeconds
        while (time < seconds) {
            val start = (time * rate).toInt()
            for (i in 0 until (BURST_SECONDS * rate).toInt()) {
                if (start + i <
                    out.size
                ) {
                    out[start + i] = (CLICK_AMPLITUDE * sin(2.0 * PI * CLICK_HZ * i / rate)).toFloat()
                }
            }
            time += period
        }
        return out
    }

    fun silence(
        seconds: Double,
        rate: Int = RATE,
    ): FloatArray = FloatArray((seconds * rate).toInt())

    fun concat(vararg parts: FloatArray): FloatArray = parts.fold(FloatArray(0)) { acc, part -> acc + part }

    fun profile(
        samples: FloatArray,
        rate: Int = RATE,
    ): SoundProfile = HandBuiltProfilerV1().analyse(MonoPcm(rate, 0L, samples))

    private const val AMPLITUDE = 0.5f
    private const val CLICK_AMPLITUDE = 0.9
    private const val CLICK_HZ = 1_000.0
    private const val BURST_SECONDS = 0.005
}
