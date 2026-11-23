package dev.tomasflopes.musicplayer.playback.bass

import android.content.Context
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import java.util.Collections
import java.util.WeakHashMap

/**
 * Builds the players that have a bass filter and remembers each one's [BassControl] (ADR 0101, 0102). The service's
 * player and the engine's spare are built here, so the outgoing and the incoming player of every overlap have one.
 * Its [of] answers [NoBassControl] for a player that it did not build.
 */
@UnstableApi
class BassPlayers(
    private val config: TransitionConfig = TransitionConfig(),
) : BassControlSource {
    private val controls = Collections.synchronizedMap(WeakHashMap<ExoPlayer, BassControl>())

    /** A player with the filter last in its sink chain; [configure] runs on its builder before it is built. */
    fun newPlayer(
        context: Context,
        configure: ExoPlayer.Builder.() -> ExoPlayer.Builder = { this },
    ): ExoPlayer {
        val processor = ShelfAudioProcessor(config)
        val player =
            ExoPlayer
                .Builder(context, BassRenderersFactory(context, processor))
                .configure()
                .build()
        controls[player] = processor
        return player
    }

    override fun of(player: ExoPlayer): BassControl = controls[player] ?: NoBassControl

    private class BassRenderersFactory(
        context: Context,
        private val processor: AudioProcessor,
    ) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean,
        ): AudioSink =
            DefaultAudioSink
                .Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessorChain(LastInChain(processor))
                .build()
    }

    /** Media3's own chain (silence skipping, then speed) with [last] appended after it. */
    private class LastInChain(
        last: AudioProcessor,
    ) : AudioProcessorChain {
        private val base = DefaultAudioSink.DefaultAudioProcessorChain()
        private val processors: Array<AudioProcessor> = base.audioProcessors + last

        override fun getAudioProcessors(): Array<AudioProcessor> = processors

        override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
            base.applyPlaybackParameters(playbackParameters)

        override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
            base.applySkipSilenceEnabled(skipSilenceEnabled)

        override fun getMediaDuration(playoutDuration: Long): Long = base.getMediaDuration(playoutDuration)

        override fun getSkippedOutputFrameCount(): Long = base.skippedOutputFrameCount
    }
}
