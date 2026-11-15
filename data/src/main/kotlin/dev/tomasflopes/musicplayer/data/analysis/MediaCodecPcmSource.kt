package dev.tomasflopes.musicplayer.data.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomasflopes.musicplayer.core.analysis.PcmBuffer
import dev.tomasflopes.musicplayer.core.analysis.PcmChunkSink
import dev.tomasflopes.musicplayer.core.analysis.PcmDecodeException
import dev.tomasflopes.musicplayer.core.analysis.PcmInfo
import dev.tomasflopes.musicplayer.core.analysis.PcmSource
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes with MediaExtractor and MediaCodec, asking for float PCM (ADR 0053). A `location` is a `content://` URI
 * (what the library stores) or a file path. Seeking uses the previous sync frame, so a window can start a little early.
 */
class MediaCodecPcmSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PcmSource {
        override fun info(location: String): PcmInfo =
            translated(location) {
                withTrack(location) { _, format ->
                    val hasDuration = format.containsKey(MediaFormat.KEY_DURATION)
                    val duration = if (hasDuration) format.getLong(MediaFormat.KEY_DURATION) else 0L
                    PcmInfo(
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                        durationMs = duration / US_PER_MS,
                    )
                }
            }

        override fun decodeAll(location: String): PcmBuffer = decode(location, startMs = 0L, lengthMs = null)

        override fun decodeWindow(
            location: String,
            startMs: Long,
            lengthMs: Long,
        ): PcmBuffer = decode(location, startMs, lengthMs)

        override fun decodeStream(
            location: String,
            sink: PcmChunkSink,
        ) {
            translated(location) {
                withTrack(location) { extractor, format ->
                    withCodec(
                        format,
                    ) { codec -> CodecLoop(extractor, codec, format, endUs = null, sink = sink).stream() }
                }
            }
        }

        private fun decode(
            location: String,
            startMs: Long,
            lengthMs: Long?,
        ): PcmBuffer =
            translated(location) {
                withTrack(location) { extractor, format ->
                    if (startMs > 0L) extractor.seekTo(startMs * US_PER_MS, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val endUs = lengthMs?.let { (startMs + it) * US_PER_MS }
                    runCodec(extractor, format, endUs)
                }
            }

        private fun runCodec(
            extractor: MediaExtractor,
            format: MediaFormat,
            endUs: Long?,
        ): PcmBuffer = withCodec(format) { codec -> CodecLoop(extractor, codec, format, endUs).run() }

        private fun <T> withCodec(
            format: MediaFormat,
            block: (MediaCodec) -> T,
        ): T {
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
                codec.configure(format, null, null, 0)
                codec.start()
                return block(codec)
            } finally {
                codec.release()
            }
        }

        private fun <T> withTrack(
            location: String,
            block: (MediaExtractor, MediaFormat) -> T,
        ): T {
            val extractor = MediaExtractor()
            try {
                if (location.startsWith(CONTENT_SCHEME)) {
                    extractor.setDataSource(context, Uri.parse(location), null)
                } else {
                    extractor.setDataSource(location)
                }
                val index = (0 until extractor.trackCount).firstOrNull { isAudio(extractor.getTrackFormat(it)) }
                val track = index ?: throw PcmDecodeException("No audio track in $location")
                extractor.selectTrack(track)
                return block(extractor, extractor.getTrackFormat(track))
            } finally {
                extractor.release()
            }
        }

        private fun isAudio(format: MediaFormat): Boolean =
            format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")

        /** Turns what the framework throws into the one failure the rest of the code knows (ADR 0053). */
        private fun <T> translated(
            location: String,
            block: () -> T,
        ): T {
            val outcome =
                try {
                    Result.success(block())
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: IllegalStateException) {
                    Result.failure(e)
                } catch (e: IllegalArgumentException) {
                    Result.failure(e)
                } catch (e: SecurityException) {
                    Result.failure(e)
                }
            return outcome.getOrElse { throw PcmDecodeException("Cannot decode $location: ${it.message}", it) }
        }

        private companion object {
            const val CONTENT_SCHEME = "content://"
            const val US_PER_MS = 1_000L
        }
    }
