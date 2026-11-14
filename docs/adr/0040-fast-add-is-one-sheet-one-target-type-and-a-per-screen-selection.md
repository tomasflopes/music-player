# 0040: Fast add is one bottom sheet over an `AddTarget`, with a per-screen `SelectionState`

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.12 wants adding from lists, search and Now Playing in a tap or two, multi-select by long press, and "Add whole album / artist". Six screens show songs (Songs, Folders, Album, Artist, dynamic lists, Search) and Now Playing shows one.

**Decision.**
- One `AddToPlaylistSheet(target)` serves every entry point. `AddTarget` is `Songs(ids)`, `Album(id)` or `Artist(id)`; the shared `AddToPlaylistViewModel` runs the right use case (ADR 0039) and returns the message. The sheet lists "New playlist" first, then playlists most recently used first, and shows the message as a Toast before it slides away (a snackbar host would need a screen-level scaffold for one line).
- `SongMenuButton` takes the song id and opens the sheet itself, as a third row "Add to playlist" next to Play next and Add to queue. Callers keep binding only the queue actions.
- Now Playing gets a "+" button beside the song text. It adds the playing song, so in Shuffle all it is the way into a playlist, as design 7.12 says.
- Long press on a `SongRow` starts a selection; while one is active a tap ticks instead of playing. `SelectionState` is a small class held with `remember` in the screen (it is about what is on screen, so no ViewModel); `SelectionBar` shows the count and "Add to playlist", and Back ends the selection. Ticked songs are added in list order, not tap order. The selection ends after an add, not when the sheet is swiped away.
- Search keeps no "more" menu on song rows; long press and the bar cover it. Search rows still log the result choice when tapped outside a selection.

**Consequences.** A new screen that lists songs gets fast add by passing three arguments to `SongRow` and placing a `SelectionBar`. The feel of the long press, the sheet and the Toast can only be judged on the phone.
