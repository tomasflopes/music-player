# 0066: PCM sources stream the song to a sink in bounded chunks

Date: 2026-11-15 · Status: accepted · Extends 0053, 0054

**Context.** ADR 0054 found that a whole-song `FloatArray` does not fit the phone's default heap, which is what the background worker runs under (ADR 0059). The profiler already takes chunks (ADR 0065), but `PcmSource` only had `decodeAll` and `decodeWindow`.

**Decision.** `PcmSource.decodeStream(location, sink)` decodes the whole file and calls `PcmChunkSink.begin(sampleRate, channels)` once, then `accept(samples, count)` for interleaved chunks of whole frames, never longer than `PcmChunkSink.MAX_CHUNK_SAMPLES` (256 Ki floats); the array is reused after `accept` returns. A file with no audio throws `PcmDecodeException`. `ProfilingSink` downmixes each chunk (same average as `MonoDownmix`) and feeds `ProfileStream`; `SoundProfiler.analyseStream` wraps both. `MediaCodecPcmSource` streams from `CodecLoop` in about 64 Ki-float chunks; `FfmpegPcmSource` reads the WAV pipe incrementally. `decodeAll` and `decodeWindow` stay for the benchmark, the golden tests and the Mac tool. Production code does not catch `OutOfMemoryError`.

**Consequences.** Memory per song is bounded by the chunk and the profiler's per-frame scalars. The contract test checks one `begin`, whole frames, the chunk limit, the length, the loudness and that the streamed profile equals the whole-file profile, for both decoders.
