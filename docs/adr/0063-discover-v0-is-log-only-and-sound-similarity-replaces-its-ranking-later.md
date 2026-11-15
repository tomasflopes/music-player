# 0063: Discover v0 is log-only; sound similarity replaces its ranking later

Date: 2026-11-14 · Status: proposed

**Context.** Design 7.7 defines Discover as never-played songs ranked by sound similarity to the owner's liked centroid. Sound profiles (phase 3) and the mix engine (phase 4) do not exist, but the owner keeps playing last added, shuffle and most played and wants variety now. The log (ADR 0025) and the artist scores (ADR 0049) already say which songs were never played and which artists are liked.

**Decision.**
- *List:* `DiscoverProvider` is a `DynamicListProvider` (id `discover`, Home order 20, play source `discover`), bound `@IntoSet` in `ExtensionPointsModule`; its Home card and list screen come from the existing generic ones.
- *Candidates:* songs with no `play_event` row at all, of any age and outcome. A new `PlayedSongSource` port in `:core:model` answers this (`SELECT DISTINCT song_id FROM play_event`, read-only, no schema change); the score board is not used, because its horizon (ADR 0023) would bring back songs played long ago.
- *Policy:* `DiscoverPolicy` in `:core:taste` is pure Kotlin; every number is in `DiscoverConfig` (size 50, at most 3 songs per main artist, artist gap 4 as in `SmartShuffleConfig`, taste scale 6, random weight 0.5). Rank = `tanh(artist score / scale)` + random part, best first, per-artist cap, then a spacing pass that keeps rank order but not the same artist inside the gap (relaxed when too few artists). `SmartShuffle` itself does not fit: it discards the ranking.
- *Random part:* a hash of one draw from the seeded `Random` and the song id, so a seed gives one list and a song that leaves because it was played does not reorder the rest. The provider is a singleton seeded with the start time: the same list for the Home card and the screen, a new one at the next app start.
- *Later:* when sound profiles exist, the rank term is replaced by similarity to the liked centroid (ADR to come, superseding this one for ranking); candidates, cap, spacing and the provider stay.

**Consequences.** Artists the owner likes lead, but only through their scores, so the list is as narrow as the taste log. The cap is hard, so a library with few unplayed artists gives a list shorter than 50. A fresh install ranks by chance alone. The list re-picks when scores or the played set change. The DAO test runs on the emulator (ADR 0009), not in `check`. Not accepted until `check` and the phone checks pass.
