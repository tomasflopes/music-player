# 0113: The lab plays a bass swap A/B and has a slider for each cheap lever

Date: 2026-11-23 · Status: proposed · Extends 0093, 0102

**Context.** The owner could not tell whether the bass swap does anything on the phone (phase 7b, hypothesis 4) and has no way to try other numbers without a build. The lab had a Bass swap switch that needs two manual runs.

**Decision.** `TransitionLab.startBassAb` plays the same pair twice with the same numbers: run A with no bass control (the engine gets `BassControlSource.None`), then run B with it, one after the other, with the label of the run shown. The lab holds its own `TransitionConfig` (`TransitionLab.config`, replaced by the sliders, read once at the start of each run, and given to a `BassPlayers` of the run so the shelf corner and depth follow). The planner is asked through `TransitionPlanner.withConfig`, so the plan line, the playable setup and the run use the slider numbers while the app keeps `TransitionConfig()`. Sliders (debug only): `overlapBars`, `minOverlapBars`, `bassCutDb`, `bassCutHz`, `bassSwapBeats`, `bassOutgoingLeadBeats`, `bassSwapDipDb`, and a button back to the app's numbers. Nothing a slider sets is saved.

**Consequences.** The volume dip of ADR 0110 stays in both runs of the A/B, so only the filter differs; set the dip slider to 0 to also remove the volume move. A run that ends early (a skip in the lab, Stop) cancels the rest of the pair. Phone-speaker and earbud results are the owner's to report; none is recorded here. The lab screen is debug-only and not unit tested, as before; the pure parts it drives are.
