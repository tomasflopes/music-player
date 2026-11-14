# 0046: One undo slot, shared by the queue and playlists; swipe to remove is one component

Date: 2026-11-14 · Status: accepted

**Context.** Swiping a song out of a playlist (ADR 0043) had no undo, and the queue gets the same swipe. Two removal places must not grow two undo mechanisms.

**Decision.**
- `UndoSlot` (interface, `:core:model`, no Android types) holds at most one pending offer. `offer(message, undo)` replaces the pending one without running it, `undo(id)` clears the slot and runs the action once, `expire(id)` clears it without running. A call with the id of a replaced offer is ignored. `SingleUndoSlot` is the implementation, a singleton from `AppModule`; `UndoSlotContract` is the contract test.
- `UndoSnackbarHost` shows the pending offer with an "Undo" action. A tap undoes; any other end of the snackbar expires the offer. The Now Playing sheet covers the shell's host, so the sheet has its own and the shell's is disabled while it is open.
- `SwipeToRemove` is the one swipe row (either direction, no background); playlists and the queue both use it.
- Playlist undo is `addSongs` then `reorder` with the order the screen showed before the removal, so the song returns to its place with no new store method. If the playlist changed meanwhile, `reorder` is ignored (ADR 0038) and the song lands at the end.
- Queue undo is `PlaybackController.restoreToQueue` (ADR 0047).

**Consequences.** Only the last removal can be undone, across both places. Leaving the app or the process dying drops the offer. Replaces nothing: playlists had no undo before.
