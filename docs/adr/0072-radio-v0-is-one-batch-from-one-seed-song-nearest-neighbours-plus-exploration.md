# 0072: Radio v0 is one batch from one seed song, nearest neighbours plus exploration

Date: 2026-11-16 · Status: proposed · Extends 0069, 0070, 0073

**Context.** Design 7.6 describes radio as a temporary mix with a liked centroid, learned state and refills. Sound vectors (0068, 0069) and the neighbour search (0073) exist now, so a first radio can start from one song without any of the mix tables.

**Decision.**
- *Seed and entry points:* one song, and its own point in the `SoundSpace` is the query (no artist seed, no average). "Start radio" is a row in the song actions sheet and a button on Now playing (seeded with the song playing now); both call the `StartRadio` use case in `:app`, which wires `SoundVectorSource`, `SongReader`, the existing `PlayedSinceSource` (no new port, no schema change) and `PlaybackController`. The UI holds no logic.
- *Batch:* `RadioPolicy` and `RadioConfig` in `:core:taste` (pure Kotlin) return 25 songs: the seed, then 24 of which about 70 % (17) are the nearest and 30 % (7) random profiled in-library songs, drawn with a seeded hash as in Discover. The exploration picks sit at even places among the nearest ones.
- *Ranking:* distance to the seed plus 0.1 for a song played in the last 2 days; such a song ranks later but is kept.
- *Shape:* copies of the seed (same file name by `SoundNeighbours.fileKey`, or same duplicate key, 0070) are never candidates and no two songs with one duplicate key share a batch. At most 3 songs per main artist, the seed counted; no main artist repeated within 4 songs, relaxed when there are too few artists (`spreadByArtist` from `ArtistPicking.kt`, shared with Discover and Rewind). The seed stays first.
- *Play:* `PlayRequest(songs, 0, shuffle = false, PlaySource.RADIO, picked = true)`. The batch replaces the queue. No refill, no learning from skips, not saved, no mix tables.
- *No profile:* a seed without a point in the space plays nothing and shows "No sound profile for this song yet". A library with fewer than 24 candidates gives a shorter batch.

**Consequences.** The radio ends after 25 songs. The recent penalty uses a set of played ids, so it cannot tell a play of an hour ago from one of a day ago. Profiles added after the first call to the space show up at the next app start (0069). All numbers are in `RadioConfig`. Not accepted until `check` and the phone checks pass.
