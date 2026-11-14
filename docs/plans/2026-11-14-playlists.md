# Playlists with fast add (design 7.12, phase 2)

Branch `feature/playlists`. Steps, one commit each: data (066e6b1), ports/Room/use cases (7a6f5f1), create and fast add (c711a39), edit and play (9feffb4), save as playlist (step 5 commit).

## Assumptions
- The song picker is a plain contains-match over the library, not the search engine (ADR 0041).
- Add results are shown as Toasts; the add sheet stays until the add finishes.
- Bulk wording: "Added N, M already there", "All M already in X", "Nothing to add".
- `PlaylistViewModel` got basic play in step 3 and editing in step 4.
- Old saved queues are ignored once after the upgrade (`QueueCodec` v2, ADR 0042).
- Swipe-remove has no undo.
- Save as playlist from the queue saves the current song and what follows; the default name is the list title ("Queue" for the queue).
- Shuffle of a playlist is the existing smart shuffle; it shows in `play_event.shuffle`, the source stays PLAYLIST.

## ADRs written
0037 tables, 0038 reorder slots, 0039 add semantics, 0040 fast add sheet and selection, 0041 Home/Library/picker, 0042 playlist id in play events, 0043 playlist page editing (reorderable library), 0044 save as playlist.

## Not run
`:data deviceCheck` emulator tests (`PlaylistDaoTest`, the migration test) were not run.

## Only the phone can verify
- Fast add feel (sheet, toasts, "+" in Now Playing).
- Long-press multi-select across screens.
- Drag reorder (handle, edge auto-scroll) and swipe remove.
- A very large playlist (thousands of songs): scrolling, reorder, Play.
- Adding from Now Playing during Shuffle all.
- Saving the queue and lists as playlists, and the Shuffle all refusal.
