# 0053: PCM decoders sit behind a `PcmSource`: ffmpeg on the Mac, MediaCodec on the phone

Date: 2026-11-14 · Status: proposed

**Context.** The Mac tool and the app must feed the same features to `:core:analysis` (ADR 0003). Decoding is the cost that matters per song and the main source of Mac/phone disagreement.

**Decision.** `PcmSource` is an interface in `:core:analysis`. `FfmpegPcmSource` (a separate `ffmpeg` process) lives in `:tools:analyser`; `MediaCodecPcmSource` (MediaExtractor + MediaCodec, float PCM) lives in `:data`. Each returns PCM at its own sample rate; downmix to mono and the 25/50/75 % windowing are done in core, once. Both run the same contract test. Other decoders (for example JLayer) can be added behind the interface and benchmarked.

**Consequences.** `check` needs `ffmpeg` on the Mac; `scripts/check-env.sh` gets a line. The MediaCodec contract test runs in `deviceCheck` (ADR 0009). `:tools:*` may depend on `:core:*` only (new onion rule, with the first `ArchitectureTest` in `:core:analysis`, ADR 0013).
