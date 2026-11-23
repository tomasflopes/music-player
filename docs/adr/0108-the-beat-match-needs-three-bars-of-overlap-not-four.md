# 0108: The beat match needs three bars of overlap, not four

Date: 2026-11-23 · Status: proposed (accepted only after a phone listen) · Extends 0092, 0094

**Context.** The owner tried Dillaz - Mo Boy (90 BPM, confidence 0.42) into Matuê - 1993 (92 BPM, confidence 0.52) in the lab and got the phase 6 crossfade, not a beat match. Both tempos pass the 0.15 gate and the stretch is 2.2 %. The cause, read from the stored profiles: the overlap has to fit the incoming intro (`overlap x r <= introEnd - entry`), 1993 has a 10.4 s intro and enters on its first bar at 0.69 s, so 9.7 s are available, and the `minOverlapBars` floor of 4 needs 10.43 s of its file (4 bars of 2.667 s, times r 0.978). Mo Boy's 7.1 s tail was not the cause: the search falls back to a switch point in the body. Songs with a short intro could therefore never beat-match, which also blocks the shorter overlap of phase 7b.

**Decision.** `minOverlapBars` is 3 (was 4). The search order, the 20 s cap, the intro rule and the switch-point rules are unchanged: the longest overlap that fits still wins, so only pairs that previously fell through to the crossfade for want of a fourth bar are affected. At 90 BPM three bars are 8 s. No schema change.

**Consequences.** More pairs are beat matched, with a shorter blend. A short overlap leaves less room for the bass swap (the swap starts on the middle bar, `3 / 2` = bar 1, and takes 2 beats), so the swap may sound more abrupt on those pairs; to be judged in the lab. Phase 7b may lower the floor further (2 bars) or make it a lab slider. Test: the Mo Boy into 1993 profiles plan a 3-bar `beat-match`; the two "four bars" crossfade tests are now "three bars".
