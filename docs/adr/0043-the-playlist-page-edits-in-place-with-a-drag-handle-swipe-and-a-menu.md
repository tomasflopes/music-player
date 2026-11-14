# 0043: The playlist page edits in place: drag handle, swipe to remove, a menu for rename and delete

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.12 wants drag reorder, swipe remove, rename and delete with confirmation, and the playlist can hold thousands of songs.

**Decision.**
- Drag reorder uses `sh.calvin.reorderable` (Compose, small, maintained) on the `LazyColumn`, from a handle at the end of each row so it does not fight the row's tap, long press or the swipe. The page drags on a local copy of the list and commits once on drop with `PlaylistViewModel.move(from, to)`, which calls `PlaylistStore.reorder` (ADR 0038). One write per drag, not one per row passed.
- Swipe (either way) removes the song at once with `removeSong`. There is no undo in this change; removing is cheap to redo from the picker.
- A top-bar menu offers Rename (the same `PlaylistNameDialog`, validated by `PlaylistName`) and Delete, which asks "Delete X? Its songs stay in your library." before `PlaylistStore.delete`; the page then closes.
- Rows keep the song's id as the list key, so reorder and removal animate.
- Hand-rolling drag was rejected: auto-scroll at the edges and item animation are the hard parts and the library already does them.

**Consequences.** One new dependency (offline-safe, no network at runtime). The feel on a phone, with large lists, can only be judged there.
