# Post-test polish (checklist)

Branch `fix/post-test-polish` from `main` at `2bb7c15` (Top 100 already merged there). Not committed. Owner runs Gradle.

- [x] 1 Search tab focuses the field on open (`SearchScreen`, `FocusRequester`). Manual check on phone.
- [x] 2 A-Z bubble: circle, 48 dp, 4 dp from the rail (`AlphabetRail`). Manual check on phone.
- [x] 3 Display artist from file name (ADR 0026)
  - [x] `DisplayArtist` + `DisplayArtistTest` (`:core:search`)
  - [x] `Song.tagArtistName`, `SongRow.toSong()` derives `artistName` (no migration)
  - [x] `SearchIndex` indexes displayed and tag artist + `SearcherTest` case
- [x] 4 Top-result card tap: artist and album open their page, song plays (`TopResultCard.onClick`). Manual check.
- [x] 5 Artist shuffle includes featurings (ADR 0027)
  - [x] `artistShuffleSongs` + `ArtistShuffleSongsTest` (`:core:model`)
  - [x] `ArtistViewModel.onShuffle`, `SearchViewModel` top-artist shuffle; tests updated/added
- [ ] Owner: `./gradlew :core:search:test :core:model:test :app:testDebugUnitTest`, then `./gradlew check`
- [ ] Owner: phone check of 1, 2, 4 and the Guitarra / Rui Veloso case; then say merge
