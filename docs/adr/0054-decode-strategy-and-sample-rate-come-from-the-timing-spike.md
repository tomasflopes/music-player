# 0054: Decode strategy, sample rate and FFT size come from the timing spike

Date: 2026-11-14 · Status: proposed (fill in the numbers after slice 1; then accept)

**Context.** Guidelines §4.3 deferred the per-song timing measurement to phase 3. Reading the audio costs far more than the features; the one costly feature step is the spectrum framing.

**Decision.** Measure 60 fixed-seed songs on the Mac (1 thread and all cores) and on the phone: (a) full decode, (b) seek-decode of three 20 s windows, (c) spectrum stub at FFT 2048/512 and 1024/512; plus a sample-rate count over 500 files. Rules: full decode if phone p95 ≤ 2 s per song, else seek; no resampling if ≥ 98 % of files are 44.1 kHz, else resample to 22.05 kHz in core; FFT 1024 if the spectrum stub is > 25 % of decode time. Write the measured numbers here, as ADR 0005 does.

**Consequences.** Full decode makes window positions exact on both sides; seeking adds small offsets, so tolerances in ADR 0060 widen. Until measured, the guess is ~0.5 s per song on the Mac and 1–2 s on the phone.
