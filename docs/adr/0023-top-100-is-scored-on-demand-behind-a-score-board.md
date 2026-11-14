# 0023: The Top 100 is scored on demand, behind a `ScoreBoard` port

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.4 turns the listening log into a decayed score per song, and 7.7 builds the Top 100 from it. The score can be computed from the log each time it is needed, or kept in a Room table that is updated as events arrive. A table needs a schema change, a tested migration and a rule for when it is stale (a changed weight or half-life makes every stored number wrong). The log is append-only and small next to the 16k-song library: a 240-day window is a few thousand rows for one listener.

**Decision.** Score on demand. `ScoreBoard.observeRanked()` returns every song with a score, best first. `OnDemandScoreBoard` reads the log rows of the last `decay.horizonMs` through `PlayEventSource.observeSince`, runs `TasteScorer` on a background dispatcher and re-emits when the log changes. `TopHundredProvider` depends on `ScoreBoard`, never on the scorer or the DAO. A cached implementation can replace `OnDemandScoreBoard` by changing one `@Provides` in `RepositoryModule`. No schema change and no migration.

**Consequences.** Nothing derived is stored, so the log stays the only truth and a change to `TasteConfig` takes effect at the next emission. Every log insert recomputes the board; a few thousand rows should take milliseconds, but this has not been measured on the phone. Switch to a cached table when a recompute measures over about 150 ms on the phone, or when a second consumer needs scores for all songs at once. That change is a new ADR that supersedes this one and ships its own non-destructive migration test.
