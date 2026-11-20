# Performance investigation (2026-11-20)

Investigation only: no production code was changed. Static analysis of `main` at `2c61fac`, plus a measurement plan to run on the phone. Library assumed at about 16k songs, play log horizon 240 days (`TasteConfig.kt:60`).

Every finding is labelled **CONFIRMED** (the code proves the behaviour) or **HYPOTHESIS** (plausible, needs measurement). CONFIRMED means the code path exists as described; how many milliseconds it costs on the phone is always a measurement (slice 4).

## Section map

| File | Covers |
|---|---|
| this file | Summary, ranked root causes, findings table, what was checked and found clean |
| [2026-11-20-perf-startup-and-data.md](2026-11-20-perf-startup-and-data.md) | Startup trace from process start to Home, scan, analysis kick-off, Room and the song mapping cost, main-thread data work |
| [2026-11-20-perf-playback-and-compose.md](2026-11-20-perf-playback-and-compose.md) | Progress bar trace, `MediaControllerPlaybackController`, service start and queue restore, Now Playing and mini-player recomposition, crossfade |
| [2026-11-20-perf-navigation-images-background.md](2026-11-20-perf-navigation-images-background.md) | Home sections and dynamic lists, tab switches, ViewModel lifetimes, covers, background scoring and analysis, memory, build config |
| [2026-11-20-perf-measurement-plan.md](2026-11-20-perf-measurement-plan.md) | How to measure each finding on the phone, A/B experiments that need no code, suggested fix order and the ADRs each fix needs |

## 1. Summary: root causes ranked by expected impact

1. **Everything is felt in a debug build.** The only documented install is `./gradlew :app:installDebug` (`README.md:52`); the convention plugin sets no build types, no R8 and no signing for release (`AndroidApplicationConventionPlugin.kt:20-33`), and there is no baseline profile. A debuggable Compose app runs without baseline profiles and with debug checks, so every other cost below is multiplied. *Symptoms: all three.* It also means numbers measured today will overstate the problem; measure on a release-like build first (slice 4, step 0).
2. **Library-sized work runs on the main thread.** Turning a database row into a `Song` runs four regexes and text folding per row (`Mappers.kt:23`, `DisplayArtist.kt:3-7,29-40`) and happens wherever the flow is collected, which for several callers is `viewModelScope` (the main thread): Discover on Home (16k songs, every start and after every track change), the queue restore at service start (every song of the saved queue, 16k after Shuffle all), Start radio / Start mix, the song picker and the mix editor. *Symptoms: slow start, tab-switch stutter, Now Playing stutter.*
3. **The progress bar is driven by a full rebuild of the player state every 500 ms.** Each tick runs a Room query for up to 220 queue songs, maps them on the main thread and publishes a new `PlaybackState` with new lists and new `Song` objects (`MediaControllerPlaybackController.kt:118-172`, `PlaybackConfig.kt:16-18`). The tick waits behind any busy database thread or busy main thread, and every visible row of Now Playing and the mini-player recomposes on each tick. It also runs while the app is in the background. *Symptoms: progress bar slow to render, Now Playing stutter, battery.*
4. **Home computes four full lists, three scoring passes and two full library loads just to draw four cards.** Top 100, Discover, Rewind and Recently added are each computed in full to show a count and one cover; nothing shows until the slowest finishes (`DynamicListsViewModel.kt:32-44`); each play event (every track change) recomputes them; and returning to Home after 5 s restarts everything (`SharingDefaults.kt:6`). A process-wide collector re-scores the log too (`PlaybackModule.kt:42-43`). *Symptoms: slow start (competes with the playlist query), Home tab-switch stutter.*
5. **Every process start runs a full scan whose last step is quadratic.** The scan always rewrites `song_feature`: it deletes the table and re-parses every title against every artist, re-sorting the artist list once per song (`LibraryScanner.kt:69-104`, `:92`; `FeaturedArtistParser.kt:30,36-46`). ADR 0014 assumed a start-up rescan is cheap; this step is not. The scan then starts phone analysis, which with up to 100 songs waiting runs with no constraints for about 9 minutes, decoding MP3s on `Dispatchers.Default` (`LibraryScanWorker.kt:31`, `AnalysisBatchWorker.kt:36`). *Symptoms: slow start, early-session jank.*
6. **The playback service starts on the main thread in the middle of app start.** Home's ViewModel injects the `PlaybackController`, whose constructor binds the service; `PlaybackService.onCreate` builds ExoPlayer, a second ExoPlayer for crossfade, the session, and restores the queue by reading the queue file and mapping every saved song on the main thread (`PlaybackService.kt:59,67-119`, `FileQueueStore.kt:11`, `QueueRestorer.kt:22-31`). Until it finishes, the controller has no state, so the mini-player and the progress bar show late. *Symptoms: slow start, progress bar slow to render after launch.*

Lower-ranked causes (covers, navigation transitions, database executor contention, memory) are in the table and slices.

## 2. Findings table

Status: C = CONFIRMED, H = HYPOTHESIS. Impact: expected, before measurement. Symptoms: S = start/playlists, P = progress bar, T = tab and Now Playing transitions, B = battery or background.

| ID | Area | Finding | St | Evidence | Sym | Impact | Verify with |
|---|---|---|---|---|---|---|---|
| B1 | Build | Only debug builds are installed; no release config, R8, signing or baseline profile | C | `README.md:52`; `AndroidApplicationConventionPlugin.kt:20-33`; no `baselineprofile` / `profileinstaller` in the build | S P T | High | Same flows on a release-like build vs debug, `am start -W`, gfxinfo |
| B2 | Build | No StrictMode, no JankStats, no trace sections: main-thread I/O is never reported | C | no `StrictMode`, `JankStats` or `trace(` in `src/main` | all | Enabler | n/a |
| B3 | Compose | No stability config; `Song`, `PlaybackState`, `Playlist` come from modules without the Compose compiler, so they are unstable and compared by identity | C | no `@Stable`/`@Immutable`/stability file; `core/model`, `playback` build without Compose | P T | Med | Compose compiler reports |
| S1 | Startup | A full library scan is requested on every process start | C | `MediaStoreObserver.kt:47-51`; ADR 0014 | S | Med | Perfetto: `library-scan` worker overlapping first frames |
| S2 | Scan | `writeFeaturedArtists` runs on every scan, even with no change: delete all, parse 16k titles, artist list re-sorted per song, per-word scan of every artist, row-by-row inserts inside one transaction | C | `LibraryScanner.kt:69-81,86-104` (sort at `:92`); `FeaturedArtistParser.kt:30,36-46` | S | High | Trace section around it; A/B in slice 4 |
| S3 | Analysis | Every scan requests analysis; with ≤100 songs waiting it runs at once, unconstrained, ~9 min of MediaCodec decode on `Default` | C | `LibraryScanWorker.kt:31`; `WorkManagerAnalysisScheduler.kt:30-36`; `AnalysisBatchWorker.kt:36`; ADR 0059 | S T B | Med | WorkManager diagnostics, Perfetto CPU |
| S4 | Startup | WorkManager is initialised on the main thread in `Application.onCreate` through the injection chain | C | `MusicPlayerApp.kt:16`; `DataModule.kt:75` | S | Low | Perfetto `bindApplication` slice |
| S5 | Startup | Playback service starts during first frames (Home VM injects the controller, constructor binds) | C | `HomeViewModel.kt:24`; `MediaControllerPlaybackController.kt:45-57`; `PlaybackService.kt:67-119` | S P | Med | Perfetto main thread |
| S6 | Restore | Queue restore reads the file and maps every saved song (chunks of 5 000) on the main thread; only ids are needed | C | `PlaybackService.kt:59`; `PlaybackEngine.kt:233-251`; `FileQueueStore.kt:11`; `QueueRestorer.kt:12,22-31`; `RoomReaders.kt:76-79` | S P | High after Shuffle all | A/B: delete `queue.bin`, compare start |
| S7 | Startup | Shell draws nothing while the folder setting loads | C | `AppShell.kt:71` | S | Low | Perfetto |
| D1 | Data | Row-to-`Song` mapping does 4 regex replaces, a split and text folding per row, every emission, in the collector's context | C | `RoomReaders.kt:39,54,78`; `Mappers.kt:23,35`; `DisplayArtist.kt:3-7,29-53` | S P T | High | Method trace of `toSong` |
| D2 | Threading | Discover maps 16k songs and runs its pick on the main thread (no `flowOn`), on every start and every play event | C | `DiscoverProvider.kt:32-39`; `DynamicListsViewModel.kt:37` | S T | High | CPU profiler, main thread |
| D3 | Threading | Start radio, artist radio and Start mix load and map the whole library on the main thread | C | `StartRadio.kt:41`; `StartArtistRadio.kt:31`; `StartMix.kt:44`; `RadioViewModel.kt:20,24` | T | Med | Trace a tap on Start radio |
| D4 | Threading | Song picker builds its index on the main thread; mix editor loads the library twice and filters 16k songs per keystroke on the main thread | C | `SongPickerViewModel.kt:56-60`; `MixEditorViewModel.kt:68-104` | T | Med | gfxinfo while typing |
| D5 | Data | Large flows (`observeAll`, 16k rows of `song.*` plus joins) exceed one CursorWindow and are re-read in full on any `song` write | H | `SongDao.kt:23-32` | S T | Med | SQLiteTime logging |
| D6 | Data | All Room work shares the default query executor; long queries and the scan transaction can delay the cheap playlist and progress queries | H | `DataModule.kt:44-47` (no executors set) | S P | Med | Perfetto thread states of Room threads |
| D7 | Data | Small synchronous file writes on the main thread on each track start, pause and play | C | `PlayEventRecorder.kt:30,36,41,49` | B | Low | StrictMode |
| P1 | Playback | Position tick = full `publish()`: Room query for up to 220 songs, mapping on main, new `PlaybackState` with new lists, every 500 ms | C | `MediaControllerPlaybackController.kt:118-172`; `PlaybackConfig.kt:6,16,18`; `PlaybackModule.kt:79` | P T B | High | Trace, Room logging |
| P2 | Playback | The tick runs whenever music plays, with or without a screen subscribed | C | `MediaControllerPlaybackController.kt:123-130` | B | Med | Perfetto with app in background |
| P3 | Playback | Every `onEvents` callback also triggers a full publish; during a crossfade ramp this may be many per second | H | `MediaControllerPlaybackController.kt:122`; `TwoPlayerCrossfadeEngine.kt:137,207` | P T | Med | Count publishes during a fade |
| P4 | Playback | Crossfade watcher wakes the main thread every 50 ms while a fade is planned; a second ExoPlayer is kept | C | `TwoPlayerCrossfadeEngine.kt:81,92,204`; `PlaybackModule.kt:65` | B | Low | Perfetto wakeups |
| P5 | Playback | Queue file fully rewritten every 15 s while playing (16k entries after Shuffle all) | C | `PlaybackEngine.kt:106-120`; `QueueSaver.kt:64-71` | B | Low | File size, trace |
| C1 | Compose | Now Playing reads the whole `PlaybackState` at the sheet's top, so the sheet, its `LazyColumn` content and every row lambda (which capture `state`) are rebuilt every tick | C | `NowPlayingSheet.kt:46,52-67,115-177` | P T | High | Layout Inspector counts |
| C2 | Compose | Each tick hands rows new `Song` objects (unstable type, identity compare), so visible `SongRow`s, the hero header and the mini-player recompose every 500 ms | C (count H) | `MediaControllerPlaybackController.kt:149,161-163`; `MiniPlayerHost.kt:15-17`; B3 | P T | High | Layout Inspector counts |
| C3 | Compose | `AlbumArt` resets to `null` on every new key and loads after the first frame, so cached covers still flash the placeholder | C | `AlbumArt.kt:35-36` | T | Low | Screen recording |
| H1 | Home | Lists section `combine`s four providers: no card until the slowest emits; each computes the full list for a count and a cover | C | `DynamicListsViewModel.kt:32-44` | S T | High | Time to first Lists card |
| H2 | Home | Three separate scoring pipelines over 240 days of logs (Top 100, Discover artists, Rewind) plus a process-wide one, each re-run on every play event; `ScoreBoard` is unscoped and not shared | C | `OnDemandScoreBoard.kt:39-56,78`; `RepositoryModule.kt:62`; `RewindProvider.kt:58-68`; `GlobalScores.kt:26`; `PlaybackModule.kt:42-43` | S T B | High | ADR 0023's 150 ms trigger |
| H3 | Home | Two full library loads on Home (Discover and Rewind each call `observeSongs()`) | C | `DiscoverProvider.kt:34`; `RewindProvider.kt:59` | S | Med | Room logging |
| H4 | Home | Analysis hint observes a whole-library aggregate join that re-runs on every profiled song and every scan | C | `AnalysisHintViewModel.kt:25`; `SoundProfileDao.kt:117-129` | T | Low | SQLiteTime |
| H5 | Home | Playlists query joins `song`, so it re-runs on every `song` write; each emission fetches covers in a second query | C | `PlaylistDao.kt:26-32`; `PlaylistsViewModel.kt:37` | S | Low | Room logging |
| N1 | Navigation | `WhileSubscribed(5 s)` plus saved tab state: coming back to a tab after 5 s restarts every flow and recomputes everything | C | `SharingDefaults.kt:6`; `AppShell.kt:266-277` | T | High | Trace a Home return after 10 s |
| N2 | Navigation | `NavHost` has no transitions, so the library default fade runs while the new screen's ViewModels start their work | C (default H) | `AppShell.kt:103-109` | T | Med | gfxinfo during switch |
| N3 | Navigation | Now Playing is a `ModalBottomSheet` (own window) created inside the Scaffold's bottom bar; each open builds the whole sheet and decodes the 300 dp hero cover | H | `AppShell.kt:244-246`; `NowPlayingHeader.kt:60`; `Spacing.kt:23` | T | Med | Trace open and close |
| I1 | Images | 16 MB cover cache keyed by size; a hero cover (~1 050 px, ~4 MB) evicts dozens of row covers; no de-duplication of in-flight loads; reads on unbounded `Dispatchers.IO` | C (effect H) | `MediaStoreCoverArtLoader.kt:24,41-45,52,59`; `CoverArtLoader.kt:29-36`; `Spacing.kt:19-23` | T | Med | Count `loadThumbnail` calls |
| I2 | Images | Artist collage avatars draw up to four covers per row | C (cost H) | `ArtistAvatar.kt:101` | T | Low | gfxinfo on Artists tab |
| M1 | Memory | Several full copies of the 16k library live at once (Search engine kept with `Lazily`, Songs tab, Discover, Rewind, pickers) | H | `SearchViewModel.kt:84-89`; D2, H3 | T | Med | Memory profiler heap dump |

## 3. Checked and found clean (not findings)

- Room indices cover the hot filters (`song` on artist, album, folder, date added, sort title; `play_event` on song and time; `playlist_song` on playlist and position): `LibraryEntities.kt:14-20`, `LogEntities.kt:17`, `PlaylistEntities.kt:35`.
- Search builds and queries its engine on `Dispatchers.Default` (`SearchViewModel.kt:84-89`, `SearchEngine.kt:52-70`). Rewind and playlist suggestions use `flowOn` (`RewindProvider.kt:68`, `PlaylistSuggestions.kt:66`). The Songs tab indexes off the main thread (`IndexedList.kt`).
- Lazy lists use stable keys; screens use `collectAsStateWithLifecycle` (one exception in `ArtistAvatar.kt:74`, harmless). No `runBlocking`, no logging in hot paths.
- Migrations only run on schema change; nothing found that queries Room on the main thread synchronously.
- `tools/analyser` is Mac-only and not part of the app.

Not investigated: the exact cost of `TasteScorer`, `DiscoverPolicy` and `RewindPolicy` on real data (needs the phone's log); the Deezer artist-photo path beyond its UI cost.

## 4. Next steps

Slice 4 has the measurement plan and a suggested fix order. Measure B1 first: until the app is measured on a release-like build, every other number mixes real cost with debug overhead.
