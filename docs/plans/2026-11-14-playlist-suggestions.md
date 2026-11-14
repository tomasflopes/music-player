# Plan: playlist suggestions, artist-based fallback (phase 2)

Spec: design §7.13. Decision: ADR 0051. Sound-based suggestions stay out (phases 3-4).

1. `:core:model`: `PlaylistDismissals` port; `InMemoryDismissals` test fixture.
2. `:core:taste`: `SuggestionConfig`; `suggest/`: `SuggestionFilter`, `SuggestionContext`, `ArtistLinkFilter`, `AlbumLinkFilter`, `TasteScoreFilter`, `DismissedNeighbourFilter`, `SuggestionRanker`, `PlaylistSuggestions` (use case, `SuggestionPlace`).
3. `:data`: table `playlist_dismissed`, `PlaylistDismissedDao`, `RoomPlaylistDismissals`, `MIGRATION_4_5`, `MusicDatabase` version 5, binding in `RepositoryModule`.
4. `:app`: `SuggestionModule` (config, filters `@IntoSet`, ranker, use case); `PlaylistSuggestionsViewModel`; `suggestionsBlock` shared by the playlist page and the song picker.
5. Tests: `SuggestionFilterContract` for each filter plus behaviour tests; `SuggestionRankerTest`, `PlaylistSuggestionsTest` (members and dismissed excluded, ranking, empty playlist, small library, size 10, live refresh, picker minimum, artist lookups once); `SuggestionConfigTest`; `PlaylistSuggestionsViewModelTest` ("+" and "✕"); emulator: `MusicDatabaseMigrationTest` 4 to 5 and `PlaylistDismissedTest`.
6. `./gradlew check`, then `./gradlew deviceCheck` for the migration. Commit `data/schemas/.../5.json`, which the build generates.

Phone checks to do:
- A playlist of one artist's songs shows songs by that artist and on the same albums, not random songs.
- "+" adds the song at the end and the row leaves the block at once; the Top 100 shows the +3 after a few more plays.
- "✕" removes the row, it stays gone after leaving and reopening the page, and it still shows in another playlist.
- Songs by a dismissed song's artist sink in that playlist's block.
- New playlist: the block appears in the picker once 2 songs are in, while the search box is empty.
- Block time on the full library (about 16 000 songs): opening the page, and after each "+" (see Open measurements in the phase 2 file).
- Upgrade over an existing install keeps playlists, history and scores (migration 4 to 5).
