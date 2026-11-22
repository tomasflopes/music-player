# 0088: Wrapped stats are aggregate queries over the log, computed on demand, with closed periods cached in memory

Date: 2026-11-22 · Status: proposed

**Context.** Design §7.10 asks for monthly and yearly look-backs from the listening log. ADR 0023 scores on demand and keeps no table; the same question comes up here. `design/performance.md` finds library-sized work on the main thread (D1 to D4, H1, H2) and says to measure before adding more; a Stats screen must not add to it. A period is a bounded slice of an append-only log, so its stats are small to compute and, once the period is over, never change.

**Decision.**
- `ListeningAggregates` (port in `:core:model`) answers one question per method with a GROUP BY (`ListeningStatsDao`): song, artist and album plays, listened time, first plays, plays per 15-minute bucket, earliest play. `StatsComputer` builds a `PeriodStats` from them; a period with no plays stops after the first query.
- Rows are read for one rule only, most skipped, because the burst rule looks at neighbouring rows (ADR 0089). That read is the rows of one period in a narrow query, never the log.
- `PeriodStatsSource` runs on `Dispatchers.Default` (`flowOn`) and recomputes the current period whenever `PlayHistoryReader.observeCount()` changes. A period is closed once it ended `StatsConfig.closedAfterMs` (1 hour) ago, the wait for a play that began before midnight and was logged after; a closed period is computed once and kept in a `ConcurrentHashMap` for the life of the process.
- Names and covers are resolved for the few ids a card shows, by id, never by reading the library.
- No table, no schema change, no migration.

**Consequences.** Nothing derived is stored, so the log stays the only truth. Not measured on the phone. If one period measures slow there (the owner's threshold: about 150 ms, as in ADR 0023), the next step is a stats cache table with its own non-destructive migration and a new ADR that supersedes this one. The cache is lost with the process, so the first open after a start recomputes each period once. The period lists re-read the earliest play on each log change (one indexed MIN).
