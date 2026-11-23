package dev.tomasflopes.musicplayer.core.model.transition

import kotlin.math.pow

/**
 * The bass filter of one player (design phase 7, slice 3, ADR 0101): a fixed low shelf at full cut ([depthDb]) mixed
 * with the dry signal, `y = x + mix * (shelf(x) - x)`. The target gain in dB is turned into the mix that gives exactly
 * that gain below the corner, and the mix moves to it one sample at a time over [rampMs]; the filter coefficients never
 * change, so the state stays valid and there is no click. At a mix of 0 (and a target of 0) [process] does nothing:
 * the samples stay as they are, bit for bit. Pure Kotlin on interleaved float samples; [setTargetDb] and [bypass] may
 * be called from another thread than [process].
 */
class BassShelf(
    sampleRate: Int,
    private val channels: Int,
    cornerHz: Double = DEFAULT_CORNER_HZ,
    val depthDb: Float = DEFAULT_DEPTH_DB,
    rampMs: Int = DEFAULT_RAMP_MS,
) {
    init {
        require(depthDb < 0f) { "The cut depth must be below 0 dB" }
    }

    private val filters =
        Array(channels) { Biquad(BiquadCoefficients.lowShelf(sampleRate, cornerHz, depthDb.toDouble())) }
    private val depthGain = 10.0.pow(depthDb / DB_PER_DECADE)
    private val slewPerFrame = 1.0 / (sampleRate * rampMs / MS_PER_SECOND)

    @Volatile private var target = 0.0
    private var mix = 0.0

    /** The gain the shelf is moving to, in dB (0 when bypassed). */
    @Volatile var targetDb: Float = 0f
        private set

    /** True when nothing is cut now and nothing is asked: [process] leaves the samples alone. */
    val isPassThrough: Boolean get() = mix == 0.0 && target == 0.0

    /** Move to [db] (clamped to the depth..0 range); NaN is ignored. */
    fun setTargetDb(db: Float) {
        if (db.isNaN()) return
        val clamped = db.coerceIn(depthDb, 0f)
        targetDb = clamped
        target = (1.0 - 10.0.pow(clamped / DB_PER_DECADE)) / (1.0 - depthGain)
    }

    /** Ramp to flat; once flat, [process] does nothing. */
    fun bypass() = setTargetDb(0f)

    /** Take the target at once (after a flush, when the audio is discontinuous anyway). */
    fun snapToDb(db: Float) {
        setTargetDb(db)
        snap()
    }

    /** Jump to the current target without a ramp. */
    fun snap() {
        mix = target
        if (mix == 0.0) filters.forEach(Biquad::reset)
    }

    /** Filters [frames] interleaved frames of [samples] in place. */
    fun process(
        samples: FloatArray,
        frames: Int,
    ) {
        val goal = target
        if (mix == 0.0 && goal == 0.0) return
        var index = 0
        repeat(frames) {
            mix = approach(mix, goal)
            for (channel in 0 until channels) {
                val dry = samples[index]
                val wet = filters[channel].process(dry)
                samples[index] = dry + (mix * (wet - dry)).toFloat()
                index++
            }
        }
        if (mix == 0.0 && goal == 0.0) filters.forEach(Biquad::reset)
    }

    private fun approach(
        current: Double,
        goal: Double,
    ): Double =
        when {
            current < goal -> minOf(goal, current + slewPerFrame)
            current > goal -> maxOf(goal, current - slewPerFrame)
            else -> current
        }

    companion object {
        const val DEFAULT_CORNER_HZ = 150.0
        const val DEFAULT_DEPTH_DB = -24f
        const val DEFAULT_RAMP_MS = 20
        private const val DB_PER_DECADE = 20.0
        private const val MS_PER_SECOND = 1000.0
    }
}
