package dev.tomasflopes.musicplayer.core.analysis

private const val CHUNK_SAMPLES = 65_536

/**
 * The swappable profiler (design §7.5, ADR 0065). A stream takes mono PCM in chunks of any size, so a decoder can feed
 * it without ever holding the whole song; [version] is stored with every profile and a change re-queues all songs.
 */
interface SoundProfiler {
    val version: Int

    fun newStream(sampleRate: Int): ProfileStream
}

interface ProfileStream {
    /** Feeds the first [count] mono samples of [samples], in -1..1 at the stream's sample rate. */
    fun feed(
        samples: FloatArray,
        count: Int = samples.size,
    )

    /** Ends the song and returns its profile. Call once. */
    fun finish(): SoundProfile
}

/** Profiles a mono buffer that is already in memory (the Mac, tests), feeding it in chunks. */
fun SoundProfiler.analyse(mono: MonoPcm): SoundProfile {
    val stream = newStream(mono.sampleRate)
    var from = 0
    while (from < mono.samples.size) {
        val count = minOf(CHUNK_SAMPLES, mono.samples.size - from)
        stream.feed(mono.samples.copyOfRange(from, from + count), count)
        from += count
    }
    return stream.finish()
}

/** Profiles decoded PCM: downmix once, then the stream. */
fun SoundProfiler.analyse(pcm: PcmBuffer): SoundProfile = analyse(MonoDownmix.toMono(pcm))
