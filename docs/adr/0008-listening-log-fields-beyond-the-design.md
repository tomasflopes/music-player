# 0008: Listening-log fields beyond the design: `songs` / `artist` sources, `started_by`, `shuffle`

Date: 2026-11-13 · Status: accepted

**Context.** Design §7.3 fixes `play_event`. Phase 1 adds a shuffle toggle (plan A1), so `source` alone no longer says whether shuffle was on; tapping a song in the Songs tab or on an Artist page has no `source` value; and §7.4 scores a song "tapped directly", which the log cannot tell apart from songs that auto-played after it. None of this can be backfilled once the log starts.

**Decision.** `source` gains `songs` and `artist` (plan G1); with shuffle on they are logged as `shuffle_all` / `artist_shuffle` (`PlaySource.withShuffle`). New column `started_by`: `picked` (owner tapped this song), `auto` (previous track ended), `nav` (next/previous) (G2). New column `shuffle` 0/1: whether shuffle was on while the song played (A4). Every chosen search result (artist, album or song) is a `search_event` (G3). Wire values are permanent strings; values may be added, never renamed.

**Consequences.** Two extra columns from schema v1, so no migration later. Later scoring reads `started_by = picked` for "tapped directly" and `shuffle` for "under shuffle".
