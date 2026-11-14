# Plan: skip-aware Recently added shuffle (phase 2, slice B)

Spec: design §7.7. Decision: ADR 0050. `SmartShuffle` and `PlainShuffle` are not changed.

1. `:core:taste`: `SkipAwareShuffleConfig`, `GlobalScores` and `BoardGlobalScores`, `SkipAwareShuffle` (smart order for neutral songs, negative songs placed later, spacing kept).
2. `:playback`: `PlayQueue` and `QueueOrdering` take a `recentlyAdded` strategy used for source `RECENTLY_ADDED`; `PlaybackModule` binds it.
3. Tests: `ShuffleStrategyContract` for the strategy with no scores and with one song in ten skipped; behaviour tests (same as smart without negatives, neutral order untouched, later on average and later still when more skipped, every song plays, spacing, neighbours, config); `RecentlyAddedShuffleQueueTest`.
4. `./gradlew check`, one commit.

Only the phone can verify: a shuffled Recently added list after skipping some of its songs a few times (they should come late, not vanish); the first shuffle after a cold start is unweighted until the board's first ranking arrives.
