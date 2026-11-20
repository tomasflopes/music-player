# 0082: The app-made lists get Play next and Add to queue through the Search wiring

Date: 2026-11-20 · Status: proposed

**Context.** A Search result has Play next and Add to queue; the four app-made lists (Recently added, Top 100, Discover, Rewind) only played from a tap. They share one screen, `DynamicListScreen`, which already has the per-screen selection of ADR 0040.

**Decision.**
- Each row gets the shared `SongMenuButton` (ADR 0040) as its trailing item, exactly as a Search row. `DynamicListViewModel.onPlayNext` and `onAddToQueue` pass the song's ref to `PlaybackController.playNext` and `addToQueue`: manual entries, logged `queue`, never moved by shuffle (ADR 0010), as in Search.
- The selection gets the same two actions. `SelectionBar` takes two optional callbacks; when a screen passes them it shows Play next and Add to queue for the ticked songs and ends the selection after either. The defaults are null, so the other five screens are unchanged.
- `onPlayNextSelected` and `onAddToQueueSelected` resolve the ticked ids against the list on screen, so the songs are queued in list order whatever the tick order, ids no longer shown are dropped, and an empty result asks the player for nothing.
- No new interface: one screen serves every `DynamicListProvider`, so a list added later gets both actions with no change.

**Consequences.** The row menu also offers Add to playlist and Start radio, as it does elsewhere. Several ticked songs queued with Play next land right after the current song in list order. The selection bar is wider by two icons; whether it fits a narrow phone, and the feel of the menu, can only be judged on the phone.
