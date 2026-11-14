# 0047: Queue removal, played history and jumps work on the `PlayQueue`, by place and song

Date: 2026-11-14 · Status: accepted

**Context.** The Now Playing sheet shows the queue and must show what was played, remove any row, and undo. ADR 0020 says the player holds only a window (20 each side) of the queue, which owns the order.

**Decision.**
- History comes from `PlayQueue` (entries before the current index), not from Media3's playlist, which holds at most 20 earlier items. No second history store. `queueLayoutOf` (`:core:model`) splits a window into played (oldest to newest), current and up next; the sheet shows at most `historyShown` (200) played rows in a lazy list. Under shuffle the queue is already in play order, so the history is the order actually played; songs skipped by a jump to a later entry also sit above.
- Rows are addressed by place in the whole queue plus song id. A stale place (the queue moved) changes nothing.
- Any entry can be removed, manual or from a mix. Removing a played or upcoming one swaps the player's window in place (`onQueueEdited`), so the current song never stops or reloads (gapless). Removing the current one ends its row (`stopped`) and the next song plays, if the player was playing; with no next song playback stops on the one before; the last song removed empties the queue.
- A removed song stays out of the queue when shuffle is turned off (the original order only restores entries still there).
- Tapping a played or upcoming song is a jump (logged `picked`) that leaves the queue intact.
- Undo reinserts the same entry (manual flag, source, playlist) at its place; a removed current song becomes current again at its position, playing only if it was.

**Consequences.** `PlaybackState` carries `played`, indices and `currentIndex`. Repeat ALL's wrapped rows have no place and cannot be removed. The current song has no swipe in the UI yet (the engine supports it).
