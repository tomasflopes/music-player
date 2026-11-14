# 0055: Profiler v1 is an ordered feature list stored as a raw float BLOB

Date: 2026-11-14 · Status: proposed

**Context.** `SoundProfiler { version; analyse(pcm): FloatArray }` (design §7.5) needs a stable stored layout, and a version change must re-queue every song.

**Decision.** `HandBuiltProfilerV1` owns an ordered list of `SoundFeature`s (tempo, RMS, RMS variation, spectral centroid, bass ratio, onset density, beat strength, zero-crossing rate), not a Hilt multibinding: a set has no order and the order is part of the stored format. Hand-written FFT, no library. The BLOB is raw float32 little-endian, in feature order. Tempo is folded into 60–180 BPM. Values are stored raw; normalising to 0–1 moves to phase 4 (p5/p95 clipping rather than min–max).

**Consequences.** Adding or changing a feature changes the profiler and `version`, which forces a new Mac run; that is intended. Results must be bit-identical for the same PCM.
