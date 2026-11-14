# 0044: Save as playlist is one use case that refuses Shuffle all and more than 500 songs

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.12: any list or the queue can be saved as a playlist, but Shuffle all (the whole library) never, and at most 500 songs.

**Decision.**
- `SaveAsPlaylist(name, songs, sources)` in `:core:model` creates the playlist through `CreatePlaylist`. It refuses, creating nothing: any source `SHUFFLE_ALL` (`ShuffleAll`), no songs (`Empty`), more than 500 distinct songs (`TooManySongs`, never truncated), a bad name (`InvalidName`). Repeats in the list go in once.
- A list screen passes its own `PlaySource`. The queue passes `QueueContent` (`PlayQueue.content()`, exposed as `PlaybackController.queueContent()`): the current song and everything after it, and the sources of the entries not queued by hand. A queue that began as Shuffle all stays refused even after songs were added by hand.
- One button, `SaveAsPlaylistButton`, asks for a name (default: the list's title, "Queue" for the queue) and shows the result as a Toast. It sits on dynamic lists, album, artist, folder and Now Playing.

**Consequences.** Songs played before the current one are not saved from the queue. The queue is read when the name is confirmed, not when the dialog opened.
