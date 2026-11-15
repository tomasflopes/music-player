# 0064: Rewind v0 is log-only and scores the past by giving the scorer only the rows before the cutoff

Date: 2026-11-15 · Status: proposed

**Context.** Design 7.7 defines Rewind as songs with a high score from more than 60 days ago and no play in the last 60 days. It needs no sound profile, only the log (ADR 0025, 0049). The open question is how to score "as of 60 days ago" without a second set of rules. `TasteScorer.scoreAll` already takes `nowEpochMs`; the decay uses it for every row's age. `OnDemandScoreBoard` (ADR 0023) reads from now minus the horizon, so it cannot answer for a past moment.

**Decision.**
- *Past scores:* `PastScorer` (`:core:taste`, pure Kotlin) keeps the rows in `[asOf - horizon, asOf)` of the three logs and calls `TasteScorer.scoreAll(rows, emptyMap(), asOf)`. A row at `asOf` counts as after it. The scorer, `Decay`, `TasteConfig` and `OnDemandScoreBoard` are not changed, so today's scores are computed exactly as before; there is no clock parameter to thread through.
- *Not played lately:* a new read-only port `PlayedSinceSource` in `:core:model` (`observePlayedIdsSince`), implemented by `RoomPlayHistory` with `SELECT DISTINCT song_id FROM play_event WHERE started_at >= :since`. No schema change. The set comes from the log, not from the scored rows, so it does not depend on the horizon.
- *Policy:* `RewindPolicy` in `:core:taste`, every number in `RewindConfig` (quiet window 60 days, minimum score then 2.0, size 50, at most 3 per main artist, artist gap 4, taste scale 6, random weight 0.3). Candidates score at least the minimum at the cutoff, are in the library and are not in the played-lately set. Rank = `tanh(score then / scale)` + a seeded random part; then the cap and spread steps Discover uses, now shared in `ArtistPicking.kt` (Discover's behaviour is unchanged).
- *Provider:* `RewindProvider` (`DynamicListProvider`, id `rewind`, Home order 30, play source `rewind`), bound `@IntoSet`, one instance per process seeded with the start time. The cutoff and the log bound are fixed when collection starts; scoring runs on `Dispatchers.Default`.

**Consequences.** The scorer stays the only place with taste rules. The list reads up to 300 days of three logs (the board reads 240) and re-picks on any change; not measured on the phone. Rows older than the horizon before the cutoff are dropped, so a song last loved more than about 300 days ago is not listed. The cutoff does not move while a list stays open. The DAO test runs on the emulator (ADR 0009). Not accepted until `check` and the phone checks pass.
