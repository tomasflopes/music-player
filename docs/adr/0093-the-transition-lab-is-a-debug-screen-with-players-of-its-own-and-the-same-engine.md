# 0093: The Transition lab is a debug screen with players of its own, driven by the same engine

Date: 2026-11-22 · Status: proposed

**Context.** Every manual check of phase 7 (alignment, drift, audibility of a speed change) must be repeatable on two chosen songs without waiting for them to meet in shuffle, and must not disturb the session, the queue or the listening log (ADR 0062). Sound neighbours (ADR 0073) is the pattern for a debug screen.

**Decision.** The Transition lab is a debug-only activity (ADR 0019: a `SettingsEntry` and a manifest entry in `app/src/debug`) with `TransitionLabViewModel`; the song search is the one of Sound neighbours. `TransitionLab` in `:playback` owns two ExoPlayers of its own and runs the `TwoPlayerCrossfadeEngine` of the app with a small `CrossfadeHost`, so what is measured is what ships; it writes no log row and touches no queue. It plays from `labLeadMs` before the switch point to 10 s after the overlap, reads `BeatPhase.errorMs` (pure, in core) and the incoming speed every 100 ms, logs every speed change, and has a 30 s drift test (both songs aligned and playing, no overlap) and a button that sets the speed 1.0, 1.04, 1.0. The owner reports from it: start alignment error, drift over 30 s, and whether the speed change is audible. The bass swap and phase lock switches are shown disabled until slices 2 and 3.

**Consequences.** The lab exists only in debug builds. Its numbers decide the open points of the spec (phase lock, 0.005 speed steps). It compiles against `:playback` only through the public `TransitionLab`, `LabSetup` and `LabState`. Not accepted until the owner has used it on the phone.
