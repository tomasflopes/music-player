# Plan: remaining taste signals (phase 2, slice A)

Spec: design §7.4. Decision: ADR 0049. Scope: song and artist scores only; mix and playlist scope are out.

1. `:core:model`: `PlaylistAddEvent`, `SongArtists`, ports `PlaylistAddSource`, `SearchEventSource`, `SongArtistSource`.
2. `:core:taste`: `TasteConfig` gains `addedToPlaylist`, `ArtistRule`, `SearchRule`; `TasteScorer.scoreAll` (songs plus artists); `ArtistShuffleStarts`; `ScoreBoard.observeArtistRanked()`; `OnDemandScoreBoard` reads the three logs.
3. `:data`: table `playlist_add_event` (schema v4, `MIGRATION_3_4`), DAOs, `RoomPlaylistStore.addSongs` writes the rows, Room implementations of the three ports, Hilt wiring in `RepositoryModule`.
4. Tests: `ArtistScoringTest`, board test for a playlist add, `ScoreBoardContract` for artists, config audit, `LogIsAppendOnlyTest`, emulator migration test 3 to 4 (not in `check`).
5. `./gradlew check`, one commit.

Not verified here: the migration on the emulator (`deviceCheck`), the recompute time with the extra reads on the phone (ADR 0023 threshold 150 ms).
