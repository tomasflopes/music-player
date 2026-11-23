# 0103: The ease-back defaults to four bars with steps of 1 %

Date: 2026-11-23 · Status: proposed · Extends 0095

**Context.** On the phone the incoming song felt slow to reach its own tempo. With `easeBars` 8 and `maxSpeedStep` 0.005 (ADR 0095) the song is stretched for the whole overlap and then for at least 8 more bars (about 21 s at 92 BPM), and for 16 bars (about 43 s) at an 8 % stretch.

**Decision.** `easeBars` 4 and `maxSpeedStep` 0.01. The count rule is unchanged, `max(easeBars, ceil(|1 - r| / maxSpeedStep))`, so a stretch up to 4 % eases over 4 bars and an 8 % stretch over 8. Only the two defaults and the tests that pinned the old numbers change. `maxNudge` stays at 0.005 (phase lock is a different mechanism).

**Alternatives.** The single-step variant (`singleStepEaseBack`, kept, off) is the fastest but the jump is the riskiest to hear. Stretching the outgoing song instead of the incoming one removes the ease-back altogether and is a larger change (a speed ramp on the outgoing player, a plan change); it is the next step if this is not enough.

**Consequences.** Each step is twice as large, so it is twice as likely to be heard; the owner listens on the phone with a 2 % and an 8 % pair in the lab. Not accepted until then. If steps are audible, set `maxSpeedStep` back toward 0.005 or build the outgoing-stretch variant.
