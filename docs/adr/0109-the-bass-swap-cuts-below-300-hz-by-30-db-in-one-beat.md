# 0109: The bass swap cuts below 300 Hz by 30 dB and takes one beat

Date: 2026-11-23 · Status: proposed (accepted only after a phone listen) · Supersedes the numbers of 0100 (the rule stays)

**Context.** After listening on the phone the owner could almost not notice the bass swap (phase 7b, hypothesis 2). The shelf sat at 150 Hz with -24 dB and the swap took 2 beats. A phone speaker and most earbuds in a pocket play little below 150 Hz, so a cut that lives there removes energy the listener barely hears, and a 2-beat linear-in-dB move is slow. These are reasons, not measurements: nothing was measured on the phone.

**Decision.** `bassCutHz` 300 (was 150), `bassCutDb` -30 (was -24), `bassSwapBeats` 1 (was 2). 300 Hz covers the kick and bass body and the low harmonics of an 808 that a small speaker does play, and still leaves the voice and the hats alone. A one-beat swap (about 0.65 s at 92 BPM) reads as a hand-over. The schedule is still linear in dB and the dry/wet ramp of ADR 0101 still removes any click. No code change: the three numbers are in `TransitionConfig` and have lab sliders (ADR 0113).

**Consequences.** The incoming song sounds thinner before the swap and the swap is easier to hear; a cut that is too high could make the incoming song sound hollow for the first bars, which only the owner can judge in the lab (the sliders go from 80 to 600 Hz and from -6 to -48 dB). Tests: `BassCurveTest` pins the slice 3 numbers explicitly, `BassLeversTest` pins these defaults.
