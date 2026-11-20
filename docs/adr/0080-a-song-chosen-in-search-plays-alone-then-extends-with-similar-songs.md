# 0080: A song chosen in Search plays alone, then extends with similar songs

Date: 2026-11-20 · Status: proposed

**Context.** Tapping a Search result queued every matching result, so the queue followed the query text, not the sound of the song.

**Decision.** A song tapped in Search (Songs section, See all, or the top card's Play on a song) starts alone at once, seeded with itself. The refiller then adds a batch of 5 songs that sound like it (`RadioPolicy.refill`, the seed excluded from the batch) and refills at 2 left or fewer. The anchor stays the chosen song for the whole queue; manual entries stay before auto songs. `RadioBatchBuilder` gains `exclude` (songs already queued, duplicate keys included) and `precedingArtists` so the artist gap holds across the seam. A song with no sound profile plays alone with no message. The Settings switch "Extend single songs" (DataStore, on by default) is read at each solo tap and each refill and affects solo only; off restores the old behaviour. No refill while repeat is on. Songs tab, albums, playlists, lists and shuffle are untouched.

**Consequences.** Tap-to-first-sound does not wait for the library. Repeat all plays only what is queued. Not measured on the full library; `check` has not run.
