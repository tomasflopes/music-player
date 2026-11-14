# 0036: The queue is saved to a file and restored, paused, when the service starts

Date: 2026-11-14 · Status: accepted

**Context.** ADR 0015 removed the media button receiver because a dead session cannot be resumed while the queue
lives only in memory (`PlayQueue.snapshot()` existed, nothing saved it). The owner wants play from a headset, a car or
the notification to carry on where they left off after the process was killed, and a cold start of the app to show the
same queue in the mini-player, paused. The queue can hold the whole library (16 000 songs, ADR 0020). Restoring must
not reshuffle (ADR 0035), must keep manual entries (ADR 0010) and must not write a `play_event` row before the owner
plays (ADR 0025). This extends ADR 0015; it edits nothing.

**Decision.**
- *Storage: one file, behind a port.* `QueueStore` (`load`, `save`) is a pure Kotlin port in `:playback`;
  `FileQueueStore` keeps `filesDir/queue/queue.bin`, replaced whole through a temporary file and an atomic move, as
  `PendingPlayStore` does for the log checkpoint. Not Room: the queue is private, throwaway state of the player, not
  library data; a table of up to 16 000 rows rewritten on every change is heavier than one blob; and Room, DAO and
  migration tests run only on a device (ADR 0009), so almost none of it would be covered by `check`. Not DataStore:
  it is meant for small preferences, not a 400 KB value. The schema stays at v2; no migration.
- *Format, versioned.* `QueueCodec` v1, big-endian: magic, version, the distinct songs once as (song id, artist id),
  the source names used (permanent `wire` values, so the enum may be reordered), the entries as (song number, source
  number, manual flag), the original order as song numbers, current index, shuffled, repeat mode, position of the
  current song, and a CRC-32 of everything before it. About 26 bytes per song: 416 KB for 16 000. Anything that is
  empty, cut short, damaged (checksum, flags, counts, index, source names) or from another version decodes to
  `null`; the service then starts with an empty queue. A format change bumps `VERSION`; an older file is ignored,
  not migrated: the queue is rebuilt by the next play.
- *When to save.* `QueueSaver` captures the state on the main thread and writes it on one background thread, in
  order. A queue change (play, next, previous, jump, Play next, Add to queue, shuffle, repeat, auto-advance, a song
  dropped from the queue) saves after `queueSaveDebounceMs` (1 s), so a burst writes once. Pause or stop saves at
  once. While playing, the position is saved every `positionSaveIntervalMs` (15 s), never on a position tick. The
  engine saves a last time, blocking, when the service is destroyed. A store that fails loses that save and nothing
  else.
- *Restore.* On service start `PlaybackEngine.restore()` loads the file, asks the library which songs still exist
  (in chunks of 5 000 ids), and `QueueRestorer` drops the ones that left it (retired, ADR 0021) from the entries and
  the original order, changing nothing else. If the current song is gone the next surviving one takes over from 0:00;
  if no song is left there is no queue (the file stays until the next save overwrites it). The queue is filled with
  `PlayQueue.restore`, which
  calls no shuffle strategy, so order, manual entries and modes come back as saved and smart shuffle is not re-run
  (ADR 0035). The player gets its window (ADR 0020), is prepared, seeks to the saved position and stays paused. A
  queue that already exists (a play request got there first) is never replaced. The app's mini-player reads the same
  singleton queue and the player's events, so a cold start shows the restored queue paused.
- *The log.* Restoring starts no row. The first time the player reports playing while the restored queue is still waiting for
  a first play, `PlaybackLog` starts the row, `started_by` auto, with the clock running; any other move (a play request,
  next, a jump) starts its own row as before and clears the wait. Releasing the engine without ever playing writes
  nothing. A resumed song is not a pick, so it is not logged as `picked`; adding a `resume` value would change the
  log's row semantics (ADR 0008) and is left to a later decision.
- *Media buttons.* The `MediaButtonReceiver` is declared again and `onPlaybackResumption` waits for the restore, then
  returns the restored window and position to Media3, which prepares and plays it.

**Consequences.** Headset, car and notification play work after the process is gone, as far as the code can be
checked on the JVM: a seeded 16 000-song queue saves in about 2 ms and restores in about 1 ms on the Mac (the test
prints the medians and only asserts under 2 s). Whether the real receiver wakes the service, and the phone's timings,
are for the owner to check. Every save rewrites the whole file (about 400 KB at 16 000 songs), also for a position
only; if that ever shows on the phone, split the position into a small file of its own. A state written by a newer
build is ignored by an older one and the other way round. Songs whose files are missing but not yet retired are not
detected here: the player fails on them and the queue drops them as before (ADR 0020).
