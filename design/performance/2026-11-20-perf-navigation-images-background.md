# Slice 3: Home, navigation, images, background work, memory and build

Part of [the performance investigation](2026-11-20-performance-investigation.md). IDs match its findings table.

## 1. Home and the dynamic lists

### H1: four full lists for four cards (CONFIRMED)

- `DynamicListsViewModel.kt:32-37` maps every provider's `songs()` to a card holding `songs.size` and `songs.firstOrNull()`, then `combine`s them (`:44`).
- `combine` emits only once every provider has emitted at least once, so the Lists row stays empty until the slowest of Top 100, Discover, Rewind and Recently added finishes.
- Each card needs a count and one cover; each provider computes its full list (Discover and Rewind over the whole library).
- Fix direction: let each card collect on its own (or `combine` with initial placeholders), and give providers a cheap "summary" path or a cached result shared with the list screen.

### H2: scoring done several times, again on every track (CONFIRMED)

- `OnDemandScoreBoard.observeRanked()` and `observeArtistRanked()` each call `observeScores()` (`OnDemandScoreBoard.kt:39-56`), and each call is a new cold pipeline: three Room flows over 240 days of logs, then `TasteScorer.scoreAll`, plus an `artistsOf` query for every scored song (`:78`).
- `RepositoryModule.kt:62`: the `ScoreBoard` is unscoped, so nothing is shared between consumers.
- Collectors at once on Home: Top 100 (`TopHundredProvider.kt:35`), Discover's artist ranking (`DiscoverProvider.kt:36`), Rewind's own scorer over the same logs (`RewindProvider.kt:58-68`). Process-wide: `BoardGlobalScores` collects `observeRanked()` for the life of the process (`GlobalScores.kt:26`, created in `PlaybackModule.kt:42-43` with the queue, so as soon as the service starts).
- Every track change inserts a `play_event` row (ADR 0025), which invalidates `play_event` and re-runs all of them; Discover also re-reads `SELECT DISTINCT song_id FROM play_event`.
- ADR 0023 already sets the trigger: switch to a cached table when a recompute measures over about 150 ms on the phone, or when a second consumer needs all scores. The second condition is already met (Top 100, Discover, global scores, suggestions).
- Fix direction: one shared scoring result per process (`shareIn` in a singleton scope with replay 1) feeding every consumer, then measure; a cached score table if the shared recompute is still above the ADR 0023 threshold. Both are ADRs (the second supersedes 0023).

### H3: two full library loads on Home (CONFIRMED)

`DiscoverProvider.kt:34` and `RewindProvider.kt:59` each call `songs.observeSongs()`: two 16k-row queries and two sets of 16k mappings, re-run on every `song` write. Search, the Songs tab and the pickers add more when open (M1).

### H4: analysis progress on Home (CONFIRMED, cost HYPOTHESIS)

`AnalysisHintViewModel.kt:25` observes `SoundProfileDao.observeProgress` (`SoundProfileDao.kt:117-129`): a 16k-row join with three aggregate sums. Room re-runs it on every write to `song`, `sound_profile` or `analysis_failure`, so once per song analysed while the batch worker runs. Each run is probably tens of milliseconds on a background thread; it matters only through executor contention (D6).

## 2. Navigation and screen lifetimes

### N1: returning to a tab restarts its work (CONFIRMED)

- `SharingDefaults.kt:6`: every screen flow uses `WhileSubscribed(5_000)`.
- `AppShell.kt:266-277`: tab switches save and restore state, so the ViewModels survive, but their collectors stop 5 s after the tab is left.
- Coming back to Home after more than 5 s restarts every Home flow: Discover on the main thread (D2), Rewind, two scoring pipelines, two library loads, the analysis aggregate, playlists and mixes. Coming back to the Songs tab re-reads and re-indexes 16k songs.
- Leaving Now Playing for a tab you left more than 5 s ago triggers the same restart on the destination.
- Fix direction: for library-sized derived data, keep results in a process-level cache that survives the ViewModel stop (or use a longer timeout for these flows); keep `WhileSubscribed` for the cheap ones.

### N2: default navigation transition (CONFIRMED config, duration HYPOTHESIS)

`AppShell.kt:103-109`: `NavHost` sets no `enterTransition` / `exitTransition`, so the navigation-compose default cross-fade runs (check its duration in navigation-compose 2.10.2). During the fade both destinations are composed and drawn, and the entering one starts its ViewModel work in the same frames. Combined with N1 this is the likely shape of the tab-switch stutter: an animation running while the main thread maps 16k songs. Fix direction: no transition or a short one for top-level tabs, and N1/D2 so the entering screen does no main-thread work.

### ViewModel scoping (checked)

Tab ViewModels are scoped to their back stack entries and restored with them; Now Playing and the mini-player are scoped to the Activity (they are created outside the `NavHost`), so they are not rebuilt by tab switches. `hiltViewModel()` inside Home sections scopes them to the Home entry. No leak found.

## 3. Images and album art

### I1: cover cache and loading (CONFIRMED setup, effect HYPOTHESIS)

- `MediaStoreCoverArtLoader.kt:24,59`: one `LruCache` of 16 MB, sized by bitmap bytes.
- `CoverArtLoader.kt:29-36`: the key includes the size in pixels, so the same album at `mini` (44 dp), `row` (52 dp), `card` (96 dp), `header` (200 dp) and `hero` (300 dp) is decoded and cached separately (`Spacing.kt:19-23`).
- A hero cover at about 1 050 px is about 4.4 MB, a quarter of the cache: opening Now Playing evicts many row covers, which then reload when the list is shown again.
- `:41-45,52`: each miss calls `loadThumbnail` on `Dispatchers.IO` with no cancellation signal and no de-duplication: ten rows of one album that enter together start ten decodes; fast scrolling can start dozens in parallel, and leaving the screen does not cancel the binder call.
- Decoding size matches display size (good); decoding is never on the main thread (good).
- Fix direction: de-duplicate in-flight loads per key, bucket sizes (small, large) instead of exact pixels, a cache sized from `ActivityManager.memoryClass`, and a bounded dispatcher. ADR 0016 deferred Coil; this is the moment to revisit it with a custom fetcher.

### I2: artist collages (CONFIRMED, cost HYPOTHESIS)

`ArtistAvatar.kt:101` draws up to four `AlbumArt` tiles per artist without a photo; each is its own load at half size. On the Artists tab a screenful of rows can start dozens of decodes. Measure with gfxinfo while flinging the Artists tab.

## 4. Background analysis and jobs

- Scan on every start (S1, S2), analysis right after (S3): slice 1.
- Scoring collectors that live as long as the process (H2) and the playback tick (P2) are the other background CPU users; with the app in the background and music playing, both keep running.
- Phone analysis writes one `sound_profile` row per song; that invalidates H4 and anything joined on `sound_profile`, but not the library lists. Sound vectors are built once per process (`RoomSoundVectorSource.kt:36-43`), so analysis does not rebuild them.

## 5. Memory and GC

### M1: several copies of the library (HYPOTHESIS)

Each `observeSongs()` subscriber holds its own 16k `Song` list (each `Song` carries path, file name, content URI, titles): Discover, Rewind, the Search engine (kept for the ViewModel's life with `SharingStarted.Lazily`, `SearchViewModel.kt:84-89`, plus its index), the Songs tab, the song picker, the mix editor. With the tick (P1) allocating up to 220 new `Song`s and four lists twice a second, plus D1's regex allocations, GC pressure is likely during playback. Verify with a heap dump (count `Song` instances) and the allocation tracker during 30 s of playback with Now Playing open.

No leak found across screens: covers are held by the LRU only; ViewModels are scoped as described in section 2.

## 6. Build and runtime configuration

### B1: debug only (CONFIRMED)

- `README.md:52` installs `:app:installDebug`. `AndroidApplicationConventionPlugin.kt:20-33` configures no `buildTypes`, so release has R8 off and no signing config; there is no way to install a release build today.
- No baseline profile module or `profileinstaller` setup; Compose's own bundled profiles are not applied to a debuggable app.
- Versions are current (`gradle/libs.versions.toml`: Compose BOM 2026.09.00, Media3 1.11.1, Room 2.8.5, Kotlin 2.4.21), so there is no library upgrade to chase first.
- Fix direction: a `benchmark` (or release) build type that is not debuggable, has R8 on and is signed with the debug key, used for every measurement and for daily use; then a Macrobenchmark module with a baseline profile generator. ADR needed (new build type, new module; ADR 0001 still holds since it runs locally).

### B2: no runtime guards (CONFIRMED)

No StrictMode policy in debug, no JankStats, no `trace` sections. Adding them in the debug source set (as ADR 0019 does for settings entries) gives the measurement plan its probes. ADR needed.

### B3: no stability configuration (CONFIRMED)

No `@Stable`/`@Immutable` and no stability configuration file; `:core:model` and `:playback` do not apply the Compose compiler. Their classes are unstable to Compose (C2). A stability configuration file in `:app` listing those packages is the usual fix and keeps `:core` free of Compose (onion rule, ADR 0013).
