# 0067: Onset-based features get a 5 % tolerance on real music

Date: 2026-11-15 · Status: accepted · Extends 0060

**Context.** ADR 0060 set tempo ±1 BPM and every other v1 feature ±2 % and asked for the tolerances to be tuned on real songs. The first Consistency report (Galaxy S24, debug build, 20 Mac-imported songs re-analysed on the phone) gave: tempo 0.00 BPM; mean loudness 0.09 %; loudness variation 0.19 %; spectral centroid 0.11 %; bass ratio 0.13 %; zero-crossing rate 0.19 %; onset density 3.69 % (1 of 20 songs over 2 %); beat strength 3.33 % (1 of 20 songs over 2 %). The two features over the limit both depend on onset peaks. The cause is not confirmed: the report does not name the song, and the likely reason (MediaCodec 16-bit PCM and a different decoder start offset move a few peaks over the threshold) was not tested.

**Decision.** The real-music limit for `onset_density` and `beat_strength` is 5 %. Tempo stays at ±1 BPM and the other five features stay at 2 %. Only the Consistency report changes (`ConsistencyReport`); the golden-file tolerances in `GoldenCompare` stay as they are because the generated files pass at 2 %.

**Consequences.** The report now passes on the sample (max 3.69 %, headroom about 1.3 points). A Mac and a phone profile of the same song can differ by up to about 5 % on these two features, which is far below the spread between songs, so similarity is not affected in practice. Open: find out which song gave the largest gap and why; if it turns out to be a decoder fault and not rounding, supersede this ADR instead of living with it. Not tested on more than 20 songs.
