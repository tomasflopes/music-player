# 0049: Artist scores, playlist adds and searches feed the `ScoreBoard` from three append-only logs

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.4 lists three signals the scorer did not read: artist-level scores (main artist in full, featured ×0.3, "started an artist shuffle" +2), "added to a playlist" +3, and searches. ADR 0023 makes scores a pure function of the log, so a signal that is not in a log cannot be scored. `playlist_song` has an `added_at`, but removing a song or deleting a playlist erases it, which would let a cleanup rewrite taste.

**Decision.**
- *Playlist adds:* a new insert-only table `playlist_add_event(song_id, playlist_id, at)` (schema v4, `MIGRATION_3_4`, no foreign key), written in the same transaction as `PlaylistStore.addSongs` for each song that really went in (a repeat adds nothing). `PlaylistAddSource` and `SearchEventSource` are new `:core:model` ports; `PlaylistAddEvent` is the row. `LogIsAppendOnlyTest` covers the new DAO.
- *Artists:* `ScoreBoard` gains `observeArtistRanked()`. An artist's score is what its songs earned after burst softening, forgiveness and decay (main artist in full, a featured artist `ArtistRule.featuredShare` = 0.3 of it) plus artist-only signals. Songs are mapped to artists through a `SongArtistSource` port (main artist from the tag, featured artists only when they are main artists in the library).
- *Artist shuffle start +2:* the log has no artist for a shuffle, so a run of consecutive `ARTIST_SHUFFLE` rows (starts at most 30 minutes apart) whose first row was picked is one start, credited to the main artist most of its songs belong to (ties: heard first). A jump inside a run is not a start.
- *Searches (rule calls where the table is silent):* the play a chosen song leads to is already a picked row (+3), so a chosen song adds nothing of its own (`SearchRule.song` = 0); a chosen artist is +1 on the artist (`SearchRule.artist`), because the page may never play; a chosen album adds nothing, its plays are logged as plays.
- All numbers are in `TasteConfig` (`SignalWeights.addedToPlaylist`, `ArtistRule`, `SearchRule`). `OnDemandScoreBoard` reads the three logs and recomputes on any change; nothing derived is stored. Mix and playlist scope stay out (phase 4, later).

**Consequences.** Every song put in a playlist counts +3, including "Save as playlist" and "Add whole album/artist"; saving the Top 100 therefore lifts those songs again. Revisit with a bulk rule if the Top 100 gets stuck. The artist lookup reads each scored song's artist rows per emission (chunked, not measured on the phone). Search and playlist rows older than the horizon are ignored, like plays. The migration test runs on the emulator (ADR 0009), not in `check`.
