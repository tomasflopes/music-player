# 0054: Decode strategy, sample rate and FFT size come from the timing spike

Date: 2026-11-14 · Status: accepted

**Context.** Guidelines §4.3 deferred the per-song timing measurement to phase 3. Reading the audio costs far more than the features; the one costly feature step is the spectrum framing.

**Decision.** Measure 60 fixed-seed songs on the Mac (1 thread and all cores) and on the phone: (a) full decode, (b) seek-decode of three 20 s windows, (c) spectrum stub at FFT 2048/512 and 1024/512; plus a sample-rate count over 500 files. Rules: full decode if phone p95 ≤ 2 s per song, else seek; no resampling if ≥ 98 % of files are 44.1 kHz, else resample to 22.05 kHz in core; FFT 1024 if the spectrum stub is > 25 % of decode time. Write the measured numbers here, as ADR 0005 does.

**Consequences.** Full decode makes window positions exact on both sides; seeking adds small offsets, so tolerances in ADR 0060 widen. Until measured, the guess is ~0.5 s per song on the Mac and 1–2 s on the phone.

**Measured (2026-11-15).** 60 fixed-seed songs (seed 20261114), medians / p95 per song.
- Mac, 1 thread: full decode 181 / 249 ms; seek-decode 144 / 154 ms; FFT 2048/512 88 / 95 ms; FFT 1024/512 41 / 45 ms. 10 threads: 60 songs in 8.4 s wall (full decode median 644 ms under load). Sample rates over 499 files: 44.1 kHz 59.3 %, 48 kHz 40.3 %, 22.05 kHz 0.4 %.
- Phone (Galaxy S24, debug build, 1 thread, `largeHeap` on): 59 of 60 songs timed, wall 1124.7 s. Full decode 14,020.9 / 24,661.5 ms; seek-decode of three 20 s windows 4,132.2 / 4,856.6 ms; FFT 2048/512 320.8 / 353.0 ms; FFT 1024/512 151.7 / 165.2 ms. Sample rates over 500 files: 44.1 kHz 55.6 %, 48 kHz 44.4 %. Estimate for 16,000 songs on the phone, 1 thread: full decode + FFT 2048/512 63 h 44 min; seek-decode + FFT 2048/512 19 h 47 min.
- One song failed with `OutOfMemoryError` (a ~273 MB whole-song buffer, refused twice at a 512 MB growth limit). Before `largeHeap` and the sized buffer, the run died at song 15 (256 MB limit; allocations of 128 MB, 69-98 MB and 133 MB).

**Outcome of the rules.**
- Decode strategy: phone full-decode p95 24.7 s > 2 s, so **seek-decode** (the phone's numbers decide).
- Resampling: 44.1 kHz share 55.6 % (Mac 59.3 %) < 98 %, so **resample to 22.05 kHz in core**.
- FFT size: the spectrum stub is 2.3 % of full-decode time on the phone (7.8 % of seek-decode) but 48.6 % on the Mac (88 of 181 ms), against the 25 % line. The rule gives 1024 on the Mac and 2048 on the phone. One size must serve both sides so profiles match, so **FFT 1024/512**: the Mac bulk run is the cost-bound side and the phone cost (151.7 ms) is acceptable.

**Memory finding.** Decoding a whole song into memory does not fit the default heap, which is what the slice 4 phone worker runs under: the default heap (256 MB growth limit) died at song 15 of 60, and even a 512 MB heap refused a ~273 MB song. Phone analysis must therefore be a streaming pass (running results, or only the windows it needs), not a speed rule alone. A whole-song feature on the phone also costs full-decode time (14 s median), which is why the bulk 16k run stays on the Mac and the phone analyses only new songs. The shape of the streaming pass is settled in the slice 2 ADR.
