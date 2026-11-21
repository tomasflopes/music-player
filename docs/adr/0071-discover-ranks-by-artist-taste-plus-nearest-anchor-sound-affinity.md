# 0071: Discover ranks by artist taste plus nearest-anchor sound affinity; anchors are diverse liked songs, not a single centroid

Date: 2026-11-21 · Status: proposed · Supersedes the ranking of 0063 · Extends 0068, 0069, 0070, 0075, 0076

**Context.** Discover v0 (0063) ranks by the main artist's score alone, so it is as narrow as the log. The sound space exists (0068, 0069) and the mixes already rank by the nearest anchor (0076). Design 7.7 says "liked centroid"; 0076 showed that a centroid of seeds from different sounds lands between them, and gym and focus music are far apart.

**Decision.**
- *Rank:* `a x tanh(artist score / 6) + s x sound term + r x random`, defaults a 1.0, s 1.0, r 0.4 (`artistWeight`, `soundWeight`, `soundRandomWeight` in `DiscoverConfig`). The artist term keeps its shape. Candidates (0070), cap 3 per main artist, artist gap 4, size 50 and `DiscoverProvider` do not change.
- *Anchors:* the 25 anchors come from the 100 best decayed song scores that are positive, in the library and profiled, taken greedily: the top scorer, then each time the song farthest from every anchor so far (`DiscoverAnchors`). They are not averaged.
- *Sound term:* the `AnchorQuery` key of 0076 (distance to the nearest anchor over that anchor's 50th-neighbour distance), as `exp(-key) - exp(-1)`: 0 at an anchor's reach, up to 0.63 on an anchor, down to -0.37 far out. A song with no profile gets 0, so it ranks by artist alone and is not buried.
- *Exploration:* 10 % of the list is drawn at random (seeded) only from the 150 candidates nearest an anchor, as radio does (0075), interleaved with the rest; one cap serves both.
- *Two rules, one provider:* `DiscoverRule` (`DiscoverInput` in, list out) is the decision point. `DiscoverPolicy` is kept unchanged in behaviour; `SoundAwareDiscoverPolicy` is the second implementation and falls back to it when there is no space or no anchor. `DiscoverModule` binds the sound-aware one. `:core:taste` stays pure Kotlin on `:core:model` only (0013).

**Evidence.** JVM tests in `:core:taste` on a one-dimensional space: a liked artist leads for 50 seeds, an unscored artist whose song sits on an anchor is lifted for 50 seeds, an anchor far from the top scorers still lifts its neighbours, and an empty anchor set gives the old list. Not run on the real library: the play log and scores live only in the phone's database, so the old and new lists have not been compared on real data.

**Consequences.** A song far from every anchor ranks below an unprofiled one. The first Home open pays the one-off space build (0069). Weights and the neutral key are untuned. Not accepted until `check` and the phone checks pass.
