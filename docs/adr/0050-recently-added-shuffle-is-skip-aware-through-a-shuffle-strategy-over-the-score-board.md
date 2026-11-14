# 0050: The Recently added shuffle is skip-aware: a `ShuffleStrategy` that reads the `ScoreBoard`

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.7: shuffling Recently added should down-weight songs with a negative global score, so a song the owner keeps skipping stops showing up early. ADR 0035 put shuffles behind `ShuffleStrategy` and made `SmartShuffle` the default; ADR 0023 puts scores behind `ScoreBoard`. A shuffle is a plain call on the main thread, while the board is a flow.

**Decision.**
- `SkipAwareShuffle` (`:core:taste`) implements `ShuffleStrategy`; `SmartShuffle` and `PlainShuffle` are untouched. Songs with no score, or a score of 0 or more, stay neutral and are ordered by a base strategy (smart shuffle, so artist spacing stays). With no negative song the result is exactly the base order.
- Each negative song is placed at a random point of the neutral order, drawn so that its expected place moves later as its score drops (Efraimidis-Spirakis key `u^(1/w)`), and only where it keeps `artistGap` tracks from its artist, looking at what precedes it, what is already placed and the next neutral songs. A song is never removed.
- Weight `w = max(minWeight, 1 / (1 + penaltyPerPoint * |score|))` for a negative score, 1 otherwise; `SkipAwareShuffleConfig(penaltyPerPoint = 0.5, minWeight = 0.05)` beside `SmartShuffleConfig` (ADR 0035 precedent).
- Scores reach the strategy through `GlobalScores.snapshot()`; `BoardGlobalScores` collects `ScoreBoard.observeRanked()` into the latest map. Until the first ranking arrives every song is unscored, so the shuffle is the base one.
- `PlayQueue` takes a third strategy, `recentlyAdded`, which `QueueOrdering` uses when the queue's source is `RECENTLY_ADDED`; Hilt binds `SkipAwareShuffle(scores, base = SmartShuffle)`. It defaults to the main strategy, so existing queues are unchanged.

**Consequences.** `BoardGlobalScores` keeps one more collector on the board for the life of the process, so each log change scores once more (ADR 0023's 150 ms threshold applies to the sum; not measured on the phone). Artist spacing around a negative song is best effort in the tail of the list. Only Recently added uses it; other lists keep smart shuffle, and the Top 100 is not shuffled by score. Extends 0035.
