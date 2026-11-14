# Smart shuffle (design 7.8), 2026-11-14

Branch `feature/smart-shuffle` from `main`. Decisions: ADR 0035.

## Assumptions
- "Main artist" is `SongRef.mainArtistId` (the tag artist, ADR 0026), not the shown artist and not featured artists.
- "Within the next 4 tracks" means at least 5 positions between two songs of one artist (`artistGap = 4`).
- The gap relaxes globally for one shuffle, to the widest the biggest artist allows, not per artist.
- Manual entries (Play next, Add to queue) count as neighbours when the automatic songs are ordered, but are never moved or checked afterwards.
- Shuffle all and a shuffled tapped song start with the whole list in the new order; the tapped or current song is the first neighbour.
- Repeat ALL re-shuffles on wrap with the end of the previous round as neighbours; manual entries are still dropped on wrap (unchanged from ADR 0010).
- Turning shuffle off, album order, folder order and a non-shuffled play never reach a strategy.
- Artist shuffle includes an `ARTIST` list shuffled on by the toggle (it becomes `ARTIST_SHUFFLE`).
- The Recently added skip-aware shuffle, playlists, sound profiles and Settings were not touched; no Settings entry for the gap.
- The Project brief status line was not updated (not asked).

## Only the phone can verify
- Shuffle all over the real library: no artist repeats inside 4 tracks, by ear and in the queue screen.
- Shuffle speed on the device with the real library size; the unit test only measures the Mac JVM.
- Turning shuffle on mid-song, with and without Play next entries queued.
- Artist shuffle (artist page and Search top result) still plain, featured songs included.
- Album and folder play stay in order; repeat ALL wrap feels right at the seam.
- Songs with no artist tag (file-name fallback, ADR 0007): they group under whatever artist id the library gives them.
