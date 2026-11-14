# 0041: Playlists appear on Home and in Library; new playlists get their first songs from a plain picker

Date: 2026-11-14 · Status: accepted

**Context.** Design 8.2 puts "Playlists" cards on Home and a Playlists tab in Library, and 7.12 says a new playlist is named and then gets its first songs (search or from any list). Search ranking and the engine are out of scope.

**Decision.**
- `PlaylistsSection` is a `HomeSection` (order 30, `@IntoSet` in `ExtensionPointsModule`): a "New playlist" card, then one card per playlist, most recently used first. Library's `pages` gets a "Playlists" page with the same data from `PlaylistsViewModel`.
- "New playlist" asks for a name (`PlaylistNameDialog`, valid names per `PlaylistName`), creates the empty playlist, opens its page and puts the song picker on top, so Back from the picker lands on the playlist.
- The picker (`SongPickerScreen`) is a search box over the whole library with ticks and an "Add N songs" button. It matches a normalised query (`SortKey`) against title, shown artist, tag artist and album, with the text built once per library snapshot, and draws at most 200 matches so 16k songs never become 16k rows. Songs already in the playlist show "Added" and cannot be ticked. It does not use the search engine of ADR 0017, so ranking is untouched.
- Routes are plain strings like the album and artist ones: `playlist/{id}` and `playlist/{id}/add`.

**Consequences.** The picker's match is simple (contains, not tiered); if it feels poor on 16k songs, it can move onto the engine in a later change. Suggestions (design 7.13) will sit on the playlist page and are not part of this change.
