# 0025: Every track the player moves to is logged, with the duration from the library

Date: 2026-11-14 · Status: accepted

**Context.** ADR 0008 fixes the `play_event` fields. In phase 1 a row started only for the song a play request began, so songs reached by next, previous, auto-advance or a queue jump left no row, the end of the last song and repeat-one loops went unlogged, and ADR 0015 notes that the queue is lost with the process. Reading the code also showed that `song_duration_ms` was read from `player.duration` right after `prepare()` (often 0), that `PlayEventTracker.pending()` stopped the listening clock, so a checkpoint could zero `listened_ms`, and that starting a row did not refresh the checkpoint, so a crash could append the same row twice.

**Decision.** `PlaybackLog` is the one place that turns player moves into log calls. `play()` logs picked or auto, a queue jump logs picked, next and previous log `nav`, an auto transition or a repeat loop ends the old row as completed and starts an auto row, an unopenable song ends as stopped. Each row carries the source of the queue it came from. The duration comes from the library `Song`, not the player. A row that starts while the player is already playing starts its clock at once. `pending()` no longer changes the tracker, and `started` refreshes the checkpoint.

**Consequences.** Scoring sees every song that played. Rows written by phase 1 builds stay as they are (the log is append-only): their `listened_ms` and `song_duration_ms` are unreliable, and the scorer treats unknown durations leniently, so an old skipped row may count as an early skip. A controller that seeks straight to a window index still bypasses the queue and the log, and previous that only restarts the song logs nothing.
