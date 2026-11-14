package dev.tomasflopes.musicplayer.core.model.crossfade

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The volume of the outgoing and the incoming track at one point of a fade, both in 0..1. */
data class FadeGains(
    val outgoing: Float,
    val incoming: Float,
)

/**
 * The equal-power crossfade curve: `outgoing = cos(p * pi / 2)` and `incoming = sin(p * pi / 2)`, so the summed
 * power stays constant (outgoing squared plus incoming squared is 1) and the middle of a fade is not quieter than
 * its ends, which a straight linear ramp would be. The ends are exact: 1 and 0 at progress 0, 0 and 1 at progress 1.
 */
object FadeCurve {
    /** [progress] is the fraction of the fade that has passed; values outside 0..1 are clamped. */
    fun gains(progress: Float): FadeGains =
        when {
            progress.isNaN() || progress <= 0f -> FadeGains(outgoing = 1f, incoming = 0f)
            progress >= 1f -> FadeGains(outgoing = 0f, incoming = 1f)
            else -> {
                val angle = progress.toDouble() * PI / 2
                FadeGains(outgoing = cos(angle).toFloat(), incoming = sin(angle).toFloat())
            }
        }
}
