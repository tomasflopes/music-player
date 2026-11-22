# 0068: Sound vectors are five normalised, weighted features under Euclidean distance

Date: 2026-11-15 · Status: superseded by 0087 · Extends 0065; replaces the ranking idea of 0063

**Context.** ADR 0065 stores raw features and leaves normalising to phase 4. Discover v1 needs one number per pair of songs that says how alike they sound. Owner decision from the 2026-11-15 brainstorm; no profiler change, so no new Mac run.

**Decision.** `SoundVectorizer` and `SoundVectorizerConfig` in `:core:analysis`, pure Kotlin. Five dimensions, in order: spectral centroid, bass energy ratio, onset density, beat strength, relative dynamics (loudness variation over mean loudness), all from the first 33 floats of the blob. Left out: tempo, mean loudness, zero-crossing rate, chroma, the energy curve. Each dimension is clipped to p5..p95 over all profiled in-library songs and scaled to 0..1 (linear-interpolated percentiles, numpy's default); beat strength then gets a square root; then each is multiplied by its weight `[1.0, 1.0, 0.5, 0.8, 0.8]`. Distance is plain Euclidean on the weighted vectors (`SoundSpace.distance`, `nearest`, shared by the app and `analyser similar`, ADR 0073). A song with a non finite feature is left out; a mean loudness of 0 gives relative dynamics 0; a feature that is equal for every song becomes 0.

**Consequences.** Percentiles move a little when songs are added, so vectors are rebuilt per process, not stored (0069). Tempo is not in the vector, so a slow and a fast song with the same timbre sit close, by decision. Valete is a known weak seed and stays as a regression seed (no profiler v2). The weights come from the brainstorm; nothing independent checks them beyond the reference lists. Not accepted until the parity check of ADR 0073 passes.
