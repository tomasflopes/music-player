package dev.tomasflopes.musicplayer.playback.bass

import android.media.audiofx.Equalizer

/**
 * Implementation 2 of [BassControl] (ADR 0102), kept to benchmark against [ShelfAudioProcessor] (CLAUDE.md decision
 * points) and not wired by default: the lowest band of the platform [Equalizer] on the player's audio session. The
 * band, its corner and its level range are the device's, so the cut is only as deep as the device allows and the steps
 * are not smoothed here. [release] frees the effect.
 */
class AudioFxBassControl(
    audioSessionId: Int,
    private val depthDb: Float = DEFAULT_DEPTH_DB,
) : BassControl {
    private val equalizer = Equalizer(PRIORITY, audioSessionId)
    private val range = equalizer.bandLevelRange

    @Volatile override var targetDb: Float = 0f
        private set

    override fun setGainDb(db: Float) {
        if (db.isNaN()) return
        targetDb = db.coerceIn(depthDb, 0f)
        val level = (targetDb * MILLIBELS_PER_DB).toInt().coerceIn(range[0].toInt(), range[1].toInt())
        equalizer.setBandLevel(BASS_BAND, level.toShort())
        equalizer.enabled = true
    }

    override fun bypass() {
        targetDb = 0f
        equalizer.enabled = false
    }

    fun release() = equalizer.release()

    private companion object {
        const val DEFAULT_DEPTH_DB = -24f
        const val PRIORITY = 0
        const val BASS_BAND: Short = 0
        const val MILLIBELS_PER_DB = 100
    }
}
