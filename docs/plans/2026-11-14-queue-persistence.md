# Queue persistence, 2026-11-14

Branch `feature/queue-persistence` from `main`. Decisions: ADR 0036 (extends ADR 0015).

## Assumptions
- Storage is one file (`filesDir/queue/queue.bin`) behind a `QueueStore` port, not Room or DataStore: no schema change
  (still v2), so no migration and nothing new for `deviceCheck`; everything is covered by `check`.
- A file that is empty, cut short, damaged (CRC-32, flags, counts, index, unknown source name) or from another format
  version is ignored and the service starts with an empty queue. There is no migration of an older file.
- "Songs removed from the library" means ids that `SongReader.songsByIds` no longer returns (retired songs, ADR 0021).
  If the current song is gone, the next surviving one takes over from 0:00; if none follows, the last one before it.
- When nothing survives, there is no queue and the file is left as it is until the next save overwrites it.
- The saved position is the position of the current song at save time. Saves: a queue change after 1 s of quiet
  (`queueSaveDebounceMs`), at once on pause or stop, every 15 s while playing (new `positionSaveIntervalMs`), and a
  blocking save when the service is destroyed. A position tick never saves.
- A resumed song starts its log row as `started_by = auto` on the first play after a restore. Adding a `resume` value
  would change the log's row semantics (out of scope), and a resume is not the owner picking a song.
- A restore never replaces a queue that already exists (a play request that got there first wins).
- Restore also sets the player's repeat mode (ONE maps to the player's repeat one, as `setRepeat` does) and announces
  shuffle and repeat to the controllers.
- The library lookup on restore asks for ids in chunks of 5 000 (one SQL `IN` list stays bounded).
- 16 000-song timing on the Mac JVM (seeded test, medians of 9 runs after 3 warm-ups): save about 2 ms, restore about
  1 ms, file 416 078 bytes (26 bytes per song). The test only asserts under 2 s each and under 48 bytes per song.
  Every save rewrites the whole file, the position included; split the position out only if the phone shows it.
- `onPlaybackResumption` waits for the restore job, then returns the player's restored window, its index and position
  to Media3. It uses the 3-argument overload (Media3 1.11.1); it fails like the default when there is no queue.
- Out of scope and untouched: smart shuffle logic, playlists, Settings entries, the log's row semantics. The project
  brief's status line is unchanged (not asked).

## Only the phone can verify
Each of these needs the real process killed (Settings > Apps > Force stop, or swipe away plus `adb shell am kill`).
1. Play a queue, pause, force stop, press play on a wired or Bluetooth headset: the song resumes near where it was
   (within the 15 s save interval if it was playing when killed, exact if paused first).
2. Same from the notification and the lock screen controls after force stop, if the notification is still shown, and
   from the Bluetooth car head unit (play, next and previous after a cold connect).
3. Reboot the phone, connect the car or headset, press play: the receiver starts the service and the queue resumes.
   Check whether a foreground service start from the media button is allowed by the OS on the phone's Android 17.
4. Open the app after a force stop: the mini-player shows the same song and queue, paused, at the saved position; the
   upcoming list, manual entries (Play next / Add to queue), shuffle and repeat match what they were.
5. Turn shuffle off after a restore: the list returns to its original order (the original order is saved).
6. Delete a song file and rescan (retire it) while the app is dead, then restore: the song is skipped; delete the
   current song: the next one starts from 0:00.
7. Restoring writes nothing to the listening log until play is pressed: after a cold start and no play, no new
   `play_event` row; after play, exactly one `auto` row for the resumed song.
8. 16 000 songs on the phone: Shuffle all, force stop, reopen; the mini-player appears without a visible delay and
   saving never janks playback. Record the save and restore times if either is noticeable.
9. A corrupt queue file (replace it with junk through Android Studio's device explorer) never crashes the service; it
   starts empty.
10. Media3's `MediaButtonReceiver` log line `onPlaybackResumption` no longer shows `UnsupportedOperationException`
    (the log line that made ADR 0015 remove the receiver).
