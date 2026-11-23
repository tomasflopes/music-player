package dev.tomasflopes.musicplayer.playback.bass

import kotlin.concurrent.thread

/**
 * What every [BassControl] does (ADR 0102): plain checks, no JUnit, so the JVM tests and the instrumented tests run
 * the same ones. [depthDb] is the full cut of the implementation under test.
 */
object BassControlContract {
    class Check(
        val name: String,
        val run: () -> Unit,
    )

    fun checks(
        depthDb: Float,
        factory: () -> BassControl,
    ): List<Check> =
        listOf(
            Check("a new control is flat") { startsFlat(factory()) },
            Check("a gain in range is kept") { keepsGain(factory()) },
            Check("a gain above 0 dB is clamped to 0") { clampsAbove(factory()) },
            Check("a gain below the depth is clamped to the depth") { clampsBelow(factory(), depthDb) },
            Check("NaN is ignored") { ignoresNaN(factory()) },
            Check("bypass goes flat and can be repeated") { bypassesTwice(factory()) },
            Check("a gain can be set again after a bypass") { setsAfterBypass(factory()) },
            Check("calls from two threads do not fail") { survivesThreads(factory(), depthDb) },
        )

    private fun startsFlat(control: BassControl) = require(control.isBypassed && control.targetDb == 0f)

    private fun keepsGain(control: BassControl) {
        control.setGainDb(-12f)
        require(control.targetDb == -12f && !control.isBypassed) { "target ${control.targetDb}" }
    }

    private fun clampsAbove(control: BassControl) {
        control.setGainDb(-12f)
        control.setGainDb(6f)
        require(control.targetDb == 0f) { "target ${control.targetDb}" }
    }

    private fun clampsBelow(
        control: BassControl,
        depthDb: Float,
    ) {
        control.setGainDb(-1_000f)
        require(control.targetDb == depthDb) { "target ${control.targetDb}" }
    }

    private fun ignoresNaN(control: BassControl) {
        control.setGainDb(-6f)
        control.setGainDb(Float.NaN)
        require(control.targetDb == -6f) { "target ${control.targetDb}" }
    }

    private fun bypassesTwice(control: BassControl) {
        control.setGainDb(-12f)
        control.bypass()
        control.bypass()
        require(control.isBypassed) { "target ${control.targetDb}" }
    }

    private fun setsAfterBypass(control: BassControl) {
        control.setGainDb(-12f)
        control.bypass()
        control.setGainDb(-3f)
        require(control.targetDb == -3f) { "target ${control.targetDb}" }
    }

    private fun survivesThreads(
        control: BassControl,
        depthDb: Float,
    ) {
        var failure: Throwable? = null
        val workers =
            List(2) { index ->
                thread {
                    try {
                        repeat(ROUNDS) { control.setGainDb(if (it % 2 == index) depthDb else 0f) }
                        control.bypass()
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Throwable,
                    ) {
                        failure = e
                    }
                }
            }
        workers.forEach(Thread::join)
        require(failure == null) { "failed: $failure" }
        require(control.targetDb in depthDb..0f)
    }

    private const val ROUNDS = 2_000
}
