# 0042: Play events log the playlist through a playlist id carried on each queue entry

Date: 2026-11-14 · Status: accepted

**Context.** `play_event.playlist_id` exists since step 1 but nothing filled it. Playing a playlist must log `PlaySource.PLAYLIST` and the playlist, in order and shuffled (shuffle shows in `play_event.shuffle`; PLAYLIST has no shuffle pair). Stats later need "what I played from playlist X".

**Decision.**
- `PlayRequest.playlistId: Long?` (default null) goes through the PlaybackCommands bundle into `PlayQueue.replace(..., playlistId)`, which stamps it on every non-manual `QueueEntry` (`QueueEntry.playlistId`, default null). Shuffle, unshuffle and relabel copy entries, so the id survives them. Manual entries (Play next, Add to queue) never carry one and log as QUEUE.
- `PlayEventTracker` writes the entry's `playlistId` into the row; `PendingPlayStore` adds it as a 9th tab field and still reads 7 and 8 fields (older checkpoints) with no playlist.
- `QueueCodec` goes to VERSION 2: a table of distinct playlist ids, and a short index on each entry (-1 = none). A saved queue of version 1 is ignored on load, by the codec's existing rule that another version decodes to null. The cost is that a queue saved by the previous build is not restored once, at the first start after updating.
- The id is a plain `Long`, not `PlaylistId`, because `:playback` does not depend on the playlist types and `PlayEvent.playlistId` is a `Long?` already.

**Consequences.** One restored queue is lost on upgrade. A playlist deleted later leaves its id in old rows, as ADR 0037 intends.
