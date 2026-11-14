package dev.tomasflopes.musicplayer.core.analysis

private const val MS_PER_SECOND = 1_000L

/** Mono PCM that remembers where in the song it starts, so a seek that landed early still cuts the right window. */
class MonoPcm(
    val sampleRate: Int,
    val startMs: Long,
    val samples: FloatArray,
) {
    /** The part from [fromMs] (song time) for [lengthMs], clipped to what this buffer holds. */
    fun window(
        fromMs: Long,
        lengthMs: Long,
    ): FloatArray {
        val from = ((fromMs - startMs) * sampleRate / MS_PER_SECOND).coerceIn(0L, samples.size.toLong())
        val to = (from + lengthMs * sampleRate / MS_PER_SECOND).coerceAtMost(samples.size.toLong())
        return samples.copyOfRange(from.toInt(), to.toInt())
    }
}

/** Mono is the average of the channels. Done here, once, for both decoders (ADR 0053). */
object MonoDownmix {
    fun toMono(pcm: PcmBuffer): MonoPcm {
        if (pcm.channels == 1) return MonoPcm(pcm.sampleRate, pcm.startMs, pcm.samples)
        val mono = FloatArray(pcm.frames)
        for (frame in mono.indices) {
            var sum = 0f
            for (channel in 0 until pcm.channels) sum += pcm.samples[frame * pcm.channels + channel]
            mono[frame] = sum / pcm.channels
        }
        return MonoPcm(pcm.sampleRate, pcm.startMs, mono)
    }
}
