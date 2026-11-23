# 0111: The beat match aims for six bars of overlap, not eight

Date: 2026-11-23 · Status: proposed (accepted only after a phone listen) · Extends 0094, 0108

**Context.** The owner found the beat match too subtle (phase 7b, hypothesis 3): when the tempos are close the stretch is small and the result is the absence of a defect, and a long blend of 8 bars (about 20 s, capped there at 90 BPM) reads as a long crossfade, not as a DJ move. A long target also keeps pairs with a short intro out of the beat match, because the longest overlap that fits is tried first. ADR 0108 lowered the floor to 3 bars; the target was not touched.

**Decision.** `overlapBars` 6 (was 8). The search is unchanged: the longest overlap from 6 down to `minOverlapBars` (3) that fits the cap, the incoming intro and the outgoing tail. At 120 BPM that is 12 s, at 90 BPM 16 s. The swap lands on the middle bar, bar 3 (was 4). `maxOverlapMs` stays 20 s.

**Consequences.** The incoming song is at full level earlier and the hand-over is more obvious; the blend is shorter, so a poor tempo match has less time to be hidden (the stretch limit of 8 % is unchanged). Pairs whose intro holds 6 bars now get 6 bars where they got 8; none loses its beat match. `TransitionPlannerTest` keeps the 8-bar numbers in a planner that sets `overlapBars = 8` explicitly, and `BassLeversTest` pins the 6-bar default. The lab slider goes from 3 to 16 bars.
