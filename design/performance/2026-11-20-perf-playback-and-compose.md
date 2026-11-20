# Slice 2: playback layer, progress bar and Now Playing rendering

Part of [the performance investigation](2026-11-20-performance-investigation.md). IDs match its findings table.

## 1. Trace: how the progress bar gets a position

| # | Thread | Step | Code |
|---|---|---|---|
| 1 | M | `MediaControllerPlaybackController` is a process singleton with a `Main.immediate` scope | `PlaybackModule.kt:67-80` |
| 2 | M | Until the `MediaController` connects, `state` is `PlaybackState()`: no song, position 0, duration 0. The connection waits for `PlaybackService.onCreate` (S5, S6) | `MediaControllerPlaybackController.kt:37,45-57` |
| 3 | M | Every `onEvents` callback emits `refresh`; while playing, a ticker emits `refresh` every 500 ms | `MediaControllerPlaybackController.kt:118-134`; `PlaybackConfig.kt:18` |
| 4 | M | `refresh.conflate().collect { publish() }`: one publish at a time, later requests merged | `:56` |
| 5 | M then Room | `publish()` takes the queue window (200 behind, 20 ahead) and calls `songs.songsByIds(...)` for up to 220 ids: a Room query with joins | `:145-149`; `PlaybackConfig.kt:6,16` |
| 6 | M | Back on main: up to 220 `toSong` mappings (D1), `queueLayoutOf`, then a new `PlaybackState` with new `upcoming`, `played`, `playedIndices`, `upcomingIndices` lists and new `Song` objects, plus `positionMs` and `durationMs` from the controller | `:150-171`; `RoomReaders.kt:76-79` |
| 7 | M | `NowPlayingSheet` collects the whole state at its top; `MiniPlayerHost` too | `NowPlayingSheet.kt:46`; `MiniPlayerHost.kt:15` |
| 8 | M | Recomposition: the sheet, the `LazyColumn` content, every visible row and the header, then `SeekBar(state.positionMs, ...)` | `NowPlayingSheet.kt:98-177`; `NowPlayingHeader.kt:51-69` |

So the bar moves only when a full `publish()` completes. Anything that delays step 5 (Room threads busy, D6) or step 6 (main thread busy: D2, S6, recomposition from step 8 itself) delays the bar. After launch the bar cannot move at all until the service has finished restoring the queue (step 2).

## 2. Findings in detail

### P1: the position tick rebuilds everything (CONFIRMED)

- Position is the only thing that changes between ticks while a song plays, yet each tick re-reads up to 220 songs from Room and rebuilds the whole state (`MediaControllerPlaybackController.kt:145-172`).
- The Room query goes to the shared executor (D6); the mapping runs on the main thread (scope `Main.immediate`, `PlaybackModule.kt:79`).
- `distinctUntilChanged` cannot help: `positionMs` differs every tick and the lists are new objects.
- Fix direction: split the state in two flows. Queue state (current song, lists, modes) published only on queue or media-item changes, with songs read from a cache the engine already keeps (`PlaybackEngine.kt:452-467`). Position as its own small flow (`positionMs`, `durationMs`, `isPlaying`), ticking only while collected, read with `currentPosition` and no database. This is an engineering decision for an ADR (it changes `PlaybackController`'s contract).

### P2: the tick runs with no screen (CONFIRMED)

`MediaControllerPlaybackController.kt:123-130` starts the ticker whenever the player is playing and stops it only when playback stops. The controller is a singleton, so with the screen off and the app in the background it still runs a Room query and 220 mappings twice a second for as long as music plays. Fix direction: drive the tick from subscription (`WhileSubscribed` on the position flow, or `subscriptionCount`).

### P3: every player event is a full publish (HYPOTHESIS)

`onEvents` emits `refresh` for any event (`:122`). Conflation keeps it to one publish at a time, but events during a crossfade ramp (`TwoPlayerCrossfadeEngine.kt:137`, every 25 ms) may reach the controller as volume or state events and keep `publish()` running back to back during a fade. Verify by counting `publish()` calls with a trace section during a fade.

### P4: crossfade polling and a second player (CONFIRMED, low)

`TwoPlayerCrossfadeEngine.kt:92` polls every 50 ms (`:204`) on the service's main scope while a fade is planned; `sparePlayer()` (`:81`) keeps a second ExoPlayer once the first fade is planned. `PlaybackModule.kt:65` makes the two-player engine the default. Small CPU, but it wakes the main thread 20 times a second for the whole song.

### P5: queue file rewrite every 15 s (CONFIRMED, low)

`PlaybackEngine.kt:106-120` saves the queue every `positionSaveIntervalMs` while playing; `QueueSaver.kt:64-71` snapshots the whole queue (16k entries after Shuffle all) on the main thread and encodes and writes it on IO. Only the position changed. Fix direction: store the position separately from the queue body, or only rewrite the body when the queue changed.

### S5, S6: service start and queue restore on the main thread (CONFIRMED)

- `PlaybackService.kt:59`: the service scope is `Main.immediate`. `onCreate` (`:67-119`) builds the ExoPlayer, the crossfade engine (`:101`), the session (`:112-118`), and calls `engine.restore()` (`:119`).
- `PlaybackEngine.kt:233-251`: `restore()` runs `QueueRestorer.load` on that scope.
- `FileQueueStore.kt:11`: `file.readBytes()` and `QueueCodec.decode` run synchronously on the main thread.
- `QueueRestorer.kt:22-31`: all distinct song ids of the saved queue are looked up in chunks of 5 000 (`:12`) through `songsByIds`, which maps every row to a `Song` on the main thread (D1), only to keep `.map { it.id }`.
- After Shuffle all, that is 16k rows read and mapped during app start, on the main thread, before the controller gets any state.
- Fix direction: decode the file on IO; ask Room only for the ids that still exist (`SELECT id FROM song WHERE in_library = 1 AND id IN (...)`), no mapping; keep the restore off the main thread except the final player calls.

## 3. Compose rendering on Now Playing and the mini-player

### C1: state read at the top of the sheet (CONFIRMED)

- `NowPlayingSheet.kt:46` reads `state` in `NowPlayingSheet`; every tick invalidates that scope.
- `NowPlayingActions` is rebuilt each time (`:52-67`); method references are probably memoised by strong skipping, so this part is cheap.
- `QueueList` and the `LazyColumn` content lambda take `state` (`:103-124`), so the item list is rebuilt every tick: `playedRows` and `upNextRows` re-run their `itemsIndexed` declarations, and each item lambda captures `state` and `actions` (`:134-176`), so every visible item's content is invalidated.
- `SeekBar` itself is fine (`SeekBar.kt:54-89`): it draws in `drawBehind`, and its label text only changes once a second.
- Fix direction: collect the position flow (P1) only inside the seek bar, or pass a `() -> Long` lambda and read it in the draw phase; pass the queue lists to the list separately so a tick never touches them.

### C2: new `Song` objects every tick (CONFIRMED path, counts HYPOTHESIS)

- `Song` is a data class in `:core:model`, built without the Compose compiler and with no stability configuration (B3), so Compose treats it as unstable. With strong skipping (on by default with the Kotlin 2.4 Compose plugin), unstable parameters are compared by identity.
- `publish()` creates new `Song` objects from Room on each tick (`MediaControllerPlaybackController.kt:149,157,161-163`), so `SongRow(song = ...)`, `AlbumArt(song, ...)`, `SongText(song)` and the mini-player's `AlbumArt` all see a new instance and recompose twice a second.
- `AlbumArt`'s `produceState` is keyed on `coverKey` (equal by value), so covers are not reloaded; the cost is recomposition and layout, not decoding.
- Expected recomposition counts with Now Playing open for 30 s while playing: about 60 per visible row. Verify with Layout Inspector (slice 4).
- Fix direction: P1's split removes the cause; a stability configuration file listing `:core:model` types (they are immutable data classes) removes the identity comparison everywhere.

### C3: covers flash the placeholder (CONFIRMED, low)

`AlbumArt.kt:35-36`: `produceState` starts at `null` and sets `value = null` before loading. Even a cache hit appears one frame later. In Now Playing, opening the sheet or changing song shows the music-note tile, then the cover; in lists, every row that enters the screen does the same. Fix direction: a synchronous cache peek used as the initial value.

### N3: opening and closing the sheet (HYPOTHESIS)

- `AppShell.kt:244-246`: the `ModalBottomSheet` is created inside the Scaffold's `bottomBar` slot; it is a separate window.
- Each open composes the whole sheet, creates `rememberLazyListState(initialFirstVisibleItemIndex = headerIndex)` (`NowPlayingSheet.kt:108-109`) and loads the 300 dp hero cover at about 1 050 px (`NowPlayingHeader.kt:60`, `Spacing.kt:23`), which is often evicted from the 16 MB cache (I1).
- While the sheet animates, the tick keeps recomposing it (C1, C2), and on close the screen below may restart flows it stopped (N1).
- Verify with a system trace of open and close: look for long `Choreographer#doFrame` slices, `loadThumbnail` on IO and `publish` on main in the same frames.
