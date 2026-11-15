package dev.tomasflopes.musicplayer.core.analysis

/**
 * Turns a decoder's interleaved chunks into a [SoundProfile]: mono downmix per frame (the same average as
 * [MonoDownmix]), then [ProfileStream.feed]. Memory stays at one chunk, whatever the song length (ADR 0054, 0065).
 */
class ProfilingSink(
    private val profiler: SoundProfiler,
) : PcmChunkSink {
    private var stream: ProfileStream? = null
    private var channels = 1
    private var mono = FloatArray(0)

    override fun begin(
        sampleRate: Int,
        channels: Int,
    ) {
        require(channels >= 1) { "A stream has at least one channel" }
        check(stream == null) { "begin was called twice" }
        this.channels = channels
        stream = profiler.newStream(sampleRate)
    }

    override fun accept(
        samples: FloatArray,
        count: Int,
    ) {
        val current = checkNotNull(stream) { "accept before begin" }
        val frames = count / channels
        if (mono.size < frames) mono = FloatArray(frames)
        if (channels == 1) {
            System.arraycopy(samples, 0, mono, 0, frames)
        } else {
            for (frame in 0 until frames) {
                var sum = 0f
                for (channel in 0 until channels) sum += samples[frame * channels + channel]
                mono[frame] = sum / channels
            }
        }
        current.feed(mono, frames)
    }

    /** The profile of everything fed so far. Call once, after the decode returned. */
    fun finish(): SoundProfile = checkNotNull(stream) { "The decoder delivered no audio" }.finish()
}

/** Profiles [location] in one streaming pass, the way the phone worker does it. */
fun SoundProfiler.analyseStream(
    source: PcmSource,
    location: String,
): SoundProfile {
    val sink = ProfilingSink(this)
    source.decodeStream(location, sink)
    return sink.finish()
}
