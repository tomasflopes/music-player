# 0039: A song is in a playlist once; adding skips repeats and says what it did

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.12 asks for one-tap and bulk adds but is silent on what happens when a song is already in the playlist. ADR 0037 makes the primary key `(playlist_id, song_id)`, so a duplicate row is impossible anyway.

**Decision.**
- Adding a song that is already there changes nothing. A single add shows "Already in <name>"; a bulk add skips the repeats and shows "Added N, M already there" (N = 0 reads "All M already in <name>").
- Repeats inside one request count once and are not reported as "already there". Order of new songs is the order given.
- Adding, even when nothing new went in, marks the playlist as recently used, so the sheet keeps listing the playlist the owner is working with first. Adding an empty list does not.
- "Add whole album" uses disc then track order (`AlbumOrder`). "Add whole artist" is the artist's own songs album by album (albums by title, tracks in order), then songs without an album; featured-on songs are not included.
- The wording lives in one pure function, `AddMessage.of`, so every entry point says the same thing.

**Consequences.** Owners who want the same song twice cannot have it; this follows from 0037 and is deliberate (a playlist is a set with an order). The use cases are plain classes in `:core:model` over the `PlaylistStore` port, tested with an in-memory fake.
