# 0100: The bass swap is a linear-in-dB schedule on the plan, over a pure low-shelf biquad

Date: 2026-11-23 · Status: proposed · Extends 0092

**Context.** Phase 7 slice 3 cuts the incoming song's bass at the start of a beat-match overlap and swaps it with the outgoing song's at the middle bar. CLAUDE.md wants the pure part in `:core:*` and the OS part behind an interface (see ADR 0101 and 0102 for the playback side).

**Decision.** `:core:model` gets `BiquadCoefficients` (RBJ low shelf, slope 1, `a0` normalised; `PASS_THROUGH` for 0 dB) and `Biquad` (transposed direct form II, so replacing the coefficients while audio runs keeps the state and the output continuous). `BassCurve` is the plan's schedule: `incomingDb(progress)` and `outgoingDb(progress)`, linear in dB, from the cut (`bassCutDb`, -24) to 0 and from 0 to the cut, starting on the middle bar of the overlap on the outgoing grid (`bars / 2`, counted from the overlap start, which is a bar start) and lasting `bassSwapBeats` (2) beats of that grid in real time. It reads the overlap progress, like `GainCurve`, so it follows the incoming position. `TransitionPlan.Overlap.bass` is nullable with a default of null: only `BeatMatchTechnique` sets it, so the crossfade and every earlier plan are unchanged.

**Consequences.** The slope-1 shelf is half way (12 dB) at the 150 Hz corner and about 23 dB down at 50 Hz, not the full 24 dB; a lower corner or a steeper slope is a number to change after the phone check. The schedule is data, so an alternative filter can use it as is. Tests are written, not run (no Gradle in the writing session).
