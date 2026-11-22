# 0091: Wrapped lives in `:core:taste` behind a port in `:core:model`, with a Room reader and registered cards

Date: 2026-11-22 · Status: proposed

**Context.** The onion rules (ADR 0013) let a core module depend on `:core:model` only, never on a sibling core module. Most skipped has to reuse `BrowsingBursts`, `SignalRules` and `TasteConfig` from `:core:taste`, so a new `:core:stats` could not use them without breaking the rule or copying them.

**Decision.**
- Pure rules in `:core:taste`, package `stats`: `StatsPeriod`, `PeriodCalendar`, `ListeningClock`, `MostSkipped`, `ComebackArtist`, `StatsComputer`, `PeriodStatsSource`; `StatsConfig` beside the other configs (ADR 0024). One class, one job.
- The port `ListeningAggregates`, its row types and `ListenedTime` in `:core:model` (`stats`), so `:data` and the contract share them. The contract and an in-memory reference are in `:core:model` test fixtures, run on the JVM (`check`) against the reference and on the emulator (`deviceCheck`) against Room, as ADR 0069 does.
- `:data`: `ListeningStatsDao` and `RoomListeningAggregates`; `StatsDataModule` builds the one `PeriodStatsSource` with the phone's time zone. No schema change.
- `:app`: the Stats tab (This month, Past months, Years) and a Wrapped page per period. Each card is a `WrappedCard` registered with `@IntoSet` in `StatsModule` (Guidelines §2.4); the screen sorts the set by `order` and drops a card without content. Cards reuse the theme, `AlbumArt` and `ArtistAvatar`.

**Consequences.** A later phase that wants Wrapped numbers on Home reads `PeriodStatsSource`. A ninth card is one class and one binding. The Room contract run needs the emulator, so `check` alone does not prove the SQL; `deviceCheck` does.
