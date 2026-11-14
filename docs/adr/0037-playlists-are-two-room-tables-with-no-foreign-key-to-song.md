# 0037: Playlists are two Room tables (schema v3); a song is in a playlist once, with no foreign key to `song`

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.12 and 9 ask for `playlist` and `playlist_song (position)`. ADR 0021 says songs are never deleted, only retired (`in_library = 0`), and every reader must hide retired ones. ADR 0009 puts Room tests on the emulator, and the migration must be non-destructive (design 12).

**Decision.**
- *Tables (migration 2 to 3, ADR 0030 style):* `playlist (id, name, created_at, updated_at, last_used_at)` and `playlist_song (playlist_id, song_id, position, added_at)`. `last_used_at` orders the add sheet, most recent first; it moves when songs are added and when a playlist is created.
- *Once per playlist:* the primary key is `(playlist_id, song_id)`, so a song cannot appear twice, whatever calls the DAO. Adding is `INSERT OR IGNORE`.
- *Foreign keys:* `playlist_song.playlist_id` cascades from `playlist` (deleting a playlist removes its rows). `song_id` has none: songs are never deleted, and a `REPLACE` anywhere in the scan would otherwise cascade into every playlist. The listening log keeps its `playlist_id` values after a delete, like `play_event` has no foreign keys (ADR 0008).
- *Retired songs:* the row stays. Readers join `song` with `in_library = 1`, so a retired song is hidden from the playlist, its count and its cover, keeps its place, and returns with the file (ADR 0021). `position` is therefore not contiguous in what the owner sees.
- *Position:* new songs go after the highest position of any row, retired ones included. A reorder rewrites only the slots the visible songs occupy (see ADR 0038), so hidden rows never move.

**Consequences.** Playlist counts and covers are computed in SQL on every emission. A song that is retired for good leaves rows behind until its playlist is deleted; there is no clean-up. Migration and DAO tests (`MusicDatabaseMigrationTest`, `PlaylistDaoTest`) run on the emulator with `deviceCheck`, not in `check`.
