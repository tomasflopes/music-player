# Slice 1: startup path and data layer

Part of [the performance investigation](2026-11-20-performance-investigation.md). IDs match its findings table.

## 1. Trace: process start to playlists visible

What happens on a cold start with a music folder already set, in order. Main thread = M, background = BG.

| # | Thread | Step | Code |
|---|---|---|---|
| 1 | M | `MusicPlayerApp.onCreate`: Hilt builds the singleton graph needed by `MediaStoreObserver`: `SettingsRepository` (DataStore), `LibraryScanController`, which calls `WorkManager.getInstance(context)`. The app is a `Configuration.Provider`, so WorkManager initialises here, on demand, on the main thread (S4) | `MusicPlayerApp.kt:16,21-25`; `DataModule.kt:72-75,82-94` |
| 2 | M | `mediaStoreObserver.start()` registers the content observer and launches two collectors on a `Default` scope | `MediaStoreObserver.kt:44-51` |
| 3 | BG | As soon as DataStore emits the saved root, `requestScan()` enqueues the unique `library-scan` work (S1) | `MediaStoreObserver.kt:47-51`; `WorkManagerScanController.kt:17-23` |
| 4 | M | `MainActivity.onCreate`: injects `CoverArtLoader` and `ArtistImageHost` (builds the Coil `ImageLoader`), requests the audio permission, `setContent` | `MainActivity.kt:29-31,43-60`; `ArtistImageModule.kt:119-128` |
| 5 | M | `AppShell` shows nothing while `ShellState.LOADING` (DataStore read), then `Tabs` (S7) | `AppShell.kt:67-75`; `ShellViewModel.kt` |
| 6 | M | `NavHost` composes Home. `HomeViewModel` is created and injects `PlaybackController`; the singleton `MediaControllerPlaybackController` is built and calls `MediaController.Builder(...).buildAsync()`, which binds `PlaybackService` (S5) | `HomeViewModel.kt:24`; `PlaybackModule.kt:67-80`; `MediaControllerPlaybackController.kt:45-57` |
| 7 | M | Home sections compose; each creates its own ViewModel and starts its flows: Shuffle all, Analysis hint (whole-library aggregate, H4), Mixes, Lists (four providers, H1-H3), Playlists | `HomeScreen.kt:40-46`; `ExtensionPointsModule.kt:31-49` |
| 8 | M | Discover's flow maps 16k rows and runs `DiscoverPolicy.pick` on the main thread (D2); Top 100 and the Discover artist ranking each start a scoring pipeline (H2); Rewind loads the library again and scores on `Default` (H3) | `DiscoverProvider.kt:32-39`; `TopHundredProvider.kt:35`; `RewindProvider.kt:52-68` |
| 9 | M | `PlaybackService.onCreate` (posted to the main looper by the bind): builds ExoPlayer, `MediaSession`, the crossfade engine; `engine.restore()` reads the queue file and maps every saved song on the main thread (S6) | `PlaybackService.kt:67-119`; `PlaybackEngine.kt:233-251` |
| 10 | BG | The scan worker runs: MediaStore query, `knownSongs()` (16k rows), plan, then the final transaction with `writeFeaturedArtists` (S2), then `analysis.request()` (S3) | `LibraryScanWorker.kt:26-31`; `LibraryScanner.kt:44-104` |
| 11 | BG/M | Playlists query (`observeAll`, joins `song`) and a second query for covers; cards render; each card cover loads after the first frame (C3) | `PlaylistDao.kt:26-32,57-58`; `PlaylistsViewModel.kt:33-39`; `AlbumArt.kt:35-36` |

Steps 7 to 11 overlap. The playlists query itself is cheap; it is slow to appear because it competes with: main-thread work in steps 8 and 9, the scan transaction holding a Room thread in step 10, and the other Home queries on the same Room executor (D6). That is a HYPOTHESIS until the trace in slice 4 shows which one dominates; the code proves all of them run at that moment.

Note on "playlists": if the symptom is the **Lists** row (Top 100, Discover, Rewind, Recently added) rather than the **Playlists** row, H1 applies directly: that row shows nothing until all four providers have emitted.

## 2. Findings in detail

### S1, S2: the start-up scan is not cheap (CONFIRMED path)

- ADR 0014 says a rescan on every start is cheap because only changed files are read. That holds for tag reading (`LibraryScanner.kt:50-60`), not for the end of the scan.
- `LibraryScanner.kt:69-81`: the closing transaction always runs and always calls `writeFeaturedArtists()`.
- `LibraryScanner.kt:86-104`: loads every artist and every title, deletes `song_feature`, then for each of the ~16k songs calls `FeaturedArtistParser.parse(title, artists.keys.sortedByDescending { it.length })`. The sort of the whole artist list is inside the loop (`:92`), so it runs 16k times.
- `FeaturedArtistParser.kt:30`: for titles with `feat`/`ft`, the list is mapped and sorted again; `:36-46`: for each word of the tail it walks every artist, building a `subList(...).joinToString(" ")` string per artist. Cost per featured title is roughly words x artists string allocations.
- Inserts are one call per row (`:94`) inside a transaction that also holds a Room transaction thread for the whole CPU-bound loop (D6).
- The rewrite invalidates `song_feature`, which re-runs any open "Featured on" list (`SongDao.kt:47-51`).

Expected impact: high on a hip-hop library where many titles carry `feat.`. Order of magnitude to confirm: 16k sorts of an artist list of a few thousand names, plus tens of thousands of joined strings, in a debug build.

Fix direction (not code): skip the rewrite when the plan changed nothing; sort once outside the loop; match tails through a map keyed by normalised first word; batch inserts; keep the CPU part outside the transaction and write the result in one short transaction. Needs an ADR that amends ADR 0014's "cheap" assumption.

### S3: analysis starts with every scan (CONFIRMED path, contention HYPOTHESIS)

- `LibraryScanWorker.kt:31` calls `analysis.request()` after each scan; `WorkManagerAnalysisScheduler.kt:30-36` enqueues when anything waits; with ≤100 waiting there are no constraints (ADR 0059).
- `AnalysisBatchWorker.kt:36` runs the batch on `Dispatchers.Default` for about 9 minutes: MediaCodec decode plus FFT work, competing with Compose and with the Discover/scoring work on the same pool.
- Each profiled song upserts `sound_profile` (H4 re-runs).
- Fix direction: an initial delay (for example a few minutes after start), `setRequiresBatteryNotLow`, and either `setRequiresDeviceIdle` for small queues or a lower-priority single-thread dispatcher. Amends ADR 0059.

### S4: WorkManager initialised on the main thread (CONFIRMED, low)

Injecting `MediaStoreObserver` into the Application pulls `LibraryScanController`, whose provider calls `WorkManager.getInstance` (`DataModule.kt:75`). On-demand initialisation then happens inside `Application.onCreate`. Usually tens of milliseconds; check the `bindApplication` slice. Fix direction: start the observer after the first frame, or provide the controller lazily.

### S7: blank shell while loading (CONFIRMED, low)

`AppShell.kt:71` draws nothing for `LOADING`. Not slow by itself, but it delays the first meaningful frame by a DataStore read and prevents measuring time to first content with `reportFullyDrawn`, which is never called.

### D1: the cost of turning a row into a `Song` (CONFIRMED)

- `RoomReaders.kt:39` maps every emitted list with `SongRow::toSong`; `songsByIds` maps too (`:76-79`).
- `Mappers.kt:23`: `DisplayArtist.of(artistName, song.fileName)`; `DisplayArtist.kt:29-40` runs `EXTENSION`, `TRACK_PREFIX`, `TITLE_SEPARATOR`, `FEATURING_TAIL` regexes; `matches` (`:46-53`) splits with `CREDIT_SPLIT` and folds each credit through `TextNormalizer.variants` (cached map lookups, but the split and the list are new each time).
- `Mappers.kt:35`: builds a new `contentUri` string per row.
- None of the readers use `flowOn`, so this runs in the collector's context. With 16k rows, every emission is about 64k regex operations plus allocations.
- The display artist only depends on the file name and the tag, both fixed at scan time (ADR 0026 chose "derived on read").
- Fix direction: compute the display artist at scan time into a column (schema change, supersedes the "derived on read" part of ADR 0026), and move mapping to `Dispatchers.Default` inside the reader with `flowOn`.

### D2, D3, D4: library-sized work on the main thread (CONFIRMED)

| Where | What runs on main | Code |
|---|---|---|
| Home Lists, Discover | 16k `toSong` + `DiscoverPolicy.pick`, on start and on each `play_event` insert | `DiscoverProvider.kt:32-39`; collected by `DynamicListsViewModel.kt:37` in `viewModelScope` |
| Discover list screen | the same work again (provider flow is not shared) | `DynamicListViewModel.kt:77-80` |
| Start radio / artist radio / mix | `observeSongs().first()` maps 16k songs before switching to `Default` | `StartRadio.kt:41`; `StartArtistRadio.kt:31`; `StartMix.kt:44`; called from `RadioViewModel.kt:20,24` |
| Song picker | 16k `toSong` + haystack per song | `SongPickerViewModel.kt:56-60` |
| Mix editor | library collected twice (labels and matches), 16k label strings, `SortKey.of` for every song on every keystroke | `MixEditorViewModel.kt:68-104` |
| Queue restore | every saved song (S6, slice 2) | `QueueRestorer.kt:31` |
| Progress tick | up to 220 songs every 500 ms (P1, slice 2) | `MediaControllerPlaybackController.kt:149` |

### D5: big result sets (HYPOTHESIS)

`SongDao.kt:23-32` selects `song.*` with two joins for 16k rows. Rows carry paths and file names, so the result likely exceeds one 2 MB CursorWindow, which the framework cursor refills by re-running the query. Every write to `song`, `artist` or `album` re-runs it for every open subscriber. Verify with `SQLiteTime` logging (slice 4). Fix direction: narrower projections for lists that only need ids, and sharing one library flow per process.

### D6: one executor for everything (HYPOTHESIS)

`DataModule.kt:44-47` builds Room with default executors, so flows, one-shot queries and transactions share Room's default IO pool. The scan's long transaction (S2), Rewind's 240-day log reads, two 16k library loads and the progress tick's `songsByIds` can all be queued at once. Verify with Perfetto thread states (Room threads busy while the playlists query waits). Confirm the pool size in the Room 2.8.5 sources before acting.

### D7: small writes on the main thread (CONFIRMED, low)

`PendingPlayStore.save` is called synchronously from `PlayEventRecorder` on track start, pause and play (`PlayEventRecorder.kt:30,36,41,49`), on the service's main scope. Each is a tiny write plus rename; StrictMode would flag it. Battery and jank impact low.

### H5: playlists query re-runs on song writes (CONFIRMED, low)

`PlaylistDao.kt:26-32` counts visible songs and picks a cover through a join on `song`, so Room re-runs it on every `song` write; `PlaylistsViewModel.kt:37` then fetches covers with `songsByIds`. Cheap with few playlists; listed because it ties the playlist row to scan writes.
