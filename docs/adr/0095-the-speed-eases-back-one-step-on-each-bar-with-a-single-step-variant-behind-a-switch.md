# 0095: The speed eases back one step on each bar, with a single-step variant behind a switch

Date: 2026-11-22 · Status: proposed

**Context.** After the overlap the incoming song is left at speed r and must return to 1.0 without an audible step (phase 7 anti-goal). The spec gives `easeBars` 8, `maxSpeedStep` 0.005, each step on a bar start, and a single step at a phrase start as the fallback if steps turn out audible; whether they do is open until the owner listens.

**Decision.** `EaseBack.steps` (pure, `:core:model`) turns the stretch into a list of `SpeedStep(atMs, speed)` in file positions of the incoming song, so no clock is involved. Stepped (default): the first step is at the overlap end (a bar start of the incoming grid), then one step on each following bar start, the speed moving evenly from r to 1.0; the count is `max(easeBars, ceil(|1 - r| / maxSpeedStep))`, so no step is above 0.005 and a stretch of 8 % takes 16 bars rather than a larger step. The last step is exactly 1.0. Single step (`TransitionConfig.singleStepEaseBack`, off): one step to 1.0 at the first phrase start of the incoming grid at or after the overlap end. The planner puts the list into the `Overlap` plan; the engine runs it after the overlap (0097). The switch is a config value, not a setting; the owner flips it in code if the steps are audible.

**Consequences.** An 8 % stretch is audible as a tempo for up to 16 bars (about 30 s at 120 BPM) and is felt less than one step of 0.08. Steps are bar-aligned only as far as the position of the player is, so each is set on the first 50 ms tick at or after its bar start. The lab plays on until the ease-back is over. Not accepted until the owner has listened to a stepped ease-back on the phone.
