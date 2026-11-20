# Slice 4: measurement plan and fix order

Part of [the performance investigation](2026-11-20-performance-investigation.md). IDs match its findings table. Package: `dev.tomasflopes.musicplayer`, activity `.MainActivity`.

Rule for every step: measure, change one thing, measure again with the same steps. Write the before and after numbers into the ADR of the fix.

## 0. Pick the build you measure (B1)

Debug numbers mix real cost with debug overhead. Two options:

- **Quick, no repo change:** measure on the debug build, but compare only before/after of one change, never absolute numbers.
- **Right way (a local, uncommitted change first, then an ADR):** a `benchmark` build type in `app/build.gradle.kts` that `initWith(release)`, is not debuggable, has R8 on, is signed with the debug key and adds `<profileable android:shell="true"/>` so Perfetto and the CPU profiler still work. If R8 breaks something (Hilt, Room and Media3 ship their own rules, so it should not), that is a finding too.

Run each experiment on both once to see how much of each symptom is debug overhead.

## 1. Startup (S1 to S7, H1 to H5, D2, D6)

Cold start time to first frame, 10 runs, take the median:

```sh
adb shell am start-activity -W -S -n dev.tomasflopes.musicplayer/.MainActivity
# read TotalTime; -S force-stops before each run
```

There is no `reportFullyDrawn()`, so "playlists visible" is not measured by this. Use a system trace for that:

1. Android Studio > Profiler > "Capture System Activities" (System Trace) with "Start this recording on startup", or a Perfetto trace from the command line.
2. In the trace, on the main thread: `bindApplication` (S4: WorkManager init inside it), `activityStart`, the first `Choreographer#doFrame`, then `handleCreateService` for `PlaybackService` (S5, S6). Note every main-thread slice over 16 ms in the first 5 s.
3. On other threads: the WorkManager thread running `LibraryScanWorker` (S2, look for its length), the analysis worker (S3), Room's executor threads (D6: are they all busy while the playlists card is empty?).
4. With CPU sampling ("Find CPU hotspots, Java/Kotlin method sample") look for `DisplayArtist.fromFileName`, `SongRow.toSong`, `DiscoverPolicy.pick`, `FeaturedArtistParser.parseTail`, `QueueRestorer.load` and which thread they run on.

A/B experiments that need no code:

| Hypothesis | Experiment | Confirms if |
|---|---|---|
| S6 queue restore | Start once after Shuffle all, force-stop, measure; then play a single album, force-stop, measure. Or move the file aside: `adb shell run-as dev.tomasflopes.musicplayer mv files/queue/queue.bin files/queue.bak` (move it back after) | Start and time to mini-player drop clearly with a small or no queue |
| S2 feature rewrite | Two cold starts in a row: the second has no new files, so all scan time beyond the listing is S2. Read `library-scan` duration in the trace | Scan worker runs for seconds with no new files |
| S3 analysis | `adb shell dumpsys jobscheduler \| grep -A3 dev.tomasflopes.musicplayer`, and WorkManager diagnostics: `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p dev.tomasflopes.musicplayer`, then `adb logcat -s WM-DiagnosticsWrkr` | `sound-profile-batch` runs in the first minute after start |
| H1 Lists row | Screen-record a cold start (`adb shell screenrecord /sdcard/start.mp4`), note when Playlists and Lists cards appear | Lists appear last and together |

## 2. Database (D1, D5, D6, H2, H4)

Turn on SQLite statement and timing logs, then restart the app:

```sh
adb shell setprop log.tag.SQLiteStatements VERBOSE
adb shell setprop log.tag.SQLiteTime VERBOSE
adb shell am force-stop dev.tomasflopes.musicplayer
adb logcat -s SQLiteStatements SQLiteTime
```

Look for: the `songsByIds` query twice a second while playing (P1), how many times the full `song` list query runs on a cold start (H3: expect two from Home), the `play_event` reads after each track change (H2), and each query's time.

Copy the database to the Mac for `EXPLAIN QUERY PLAN` and timings (or use App Inspection > Database Inspector on the debug build):

```sh
for f in music.db music.db-wal music.db-shm; do
  adb exec-out run-as dev.tomasflopes.musicplayer cat databases/$f > $f
done
sqlite3 music.db
```

In `sqlite3`, `.timer on`, then run `EXPLAIN QUERY PLAN` and the query itself for: the library list (`SongDao.kt:23-32`), the analysis progress (`SoundProfileDao.kt:117-125`), the 240-day log read (`PlayEventDao.kt:19-20`) and the playlists list (`PlaylistDao.kt:28-32,57`). Also record the sizes the estimates depend on:

```sql
SELECT COUNT(*) FROM song WHERE in_library = 1;
SELECT COUNT(*) FROM artist;
SELECT COUNT(*) FROM song WHERE title LIKE '%feat%' OR title LIKE '% ft%';
SELECT COUNT(*) FROM play_event WHERE started_at >= (strftime('%s','now') - 240*86400) * 1000;
SELECT SUM(LENGTH(path) + LENGTH(file_name) + LENGTH(title) + LENGTH(folder_path)) FROM song;  -- D5 vs a 2 MB window
```

## 3. Now Playing and the progress bar (P1 to P5, C1 to C3, N3)

1. **Recomposition counts.** Layout Inspector on the running app with recomposition counts on. Play a song, open Now Playing, reset the counts, wait 30 s. C1 and C2 are confirmed if `SongRow`, `AlbumArt` and `NowPlayingHeader` show about 60 recompositions; with the fix they should show 0 and only the seek bar should change.
2. **Cost per tick.** System trace with CPU sampling for 30 s while playing with the sheet open. Find `MediaControllerPlaybackController.publish` on the main thread and its share of each 500 ms.
3. **Background (P2).** Start playback, turn the screen off, record a 60 s system trace. Expect main-thread wake-ups every 500 ms and a Room query each time if P2 holds.
4. **During a fade (P3, P4).** With crossfade on, trace the last 15 s of a song: count `publish` calls and main-thread wakeups.
5. **Time to a moving bar after launch (S5, S6).** Screen-record a cold start, tap the mini-player as soon as it shows, and measure until the bar first moves after pressing play. Compare with a small saved queue (section 1).
6. **Frames.** `adb shell dumpsys gfxinfo dev.tomasflopes.musicplayer reset`, open and close the sheet 10 times, then `adb shell dumpsys gfxinfo dev.tomasflopes.musicplayer` and read "Janky frames" and the 90th/99th percentiles. Add `framestats` for per-frame detail.

## 4. Tab switches (N1, N2, D2, I1, I2)

1. Reset gfxinfo, then 5 times: Home, Library, **wait 10 s**, Home. Read janky frames and percentiles.
2. Repeat, but wait only **2 s** before going back. If jank is much lower at 2 s, N1 (the 5 s `WhileSubscribed` restart) is confirmed.
3. Repeat 1 with a system trace and check what runs on the main thread during the transition (D2: Discover pick and mapping).
4. Fling the Artists and Songs tabs for 10 s each with gfxinfo reset before each (I1, I2).
5. Open Now Playing, close it, scroll the Songs tab back to the top: count `loadThumbnail` calls in the trace (I1 eviction by the hero cover).

## 5. Compose compiler reports (B3, C2)

Local, uncommitted change in `app/build.gradle.kts`:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

Build, then check `app_release-classes.txt` / `app_debug-classes.txt` and `*-composables.txt`: whether `Song` and `PlaybackState` parameters are reported unstable, and whether `SongRow`, `AlbumArt`, `MiniPlayer` and `NowPlayingHeader` are skippable.

## 6. Memory (M1)

Android Studio Memory profiler on the debug build: visit Home, Library > Songs, Search, then force a GC and take a heap dump. Count `dev.tomasflopes.musicplayer.core.model.Song` instances (one library copy is about 16k). Then record allocations for 30 s of playback with Now Playing open (P1 allocations).

## 7. Probes worth adding first (local, debug source set; ADR before committing)

- `androidx.tracing` sections around `publish()`, `QueueRestorer.load`, `writeFeaturedArtists`, `DiscoverPolicy.pick`, `OnDemandScoreBoard.rank`, so they show by name in traces and in Macrobenchmark `TraceSectionMetric`.
- A StrictMode thread policy (`detectDiskReads`, `detectDiskWrites`, `penaltyLog`) set from a debug-only initializer, like the debug settings entries of ADR 0019.
- `reportFullyDrawn()` when the Home sections have their first data, to measure time to full display.
- Later: a `:benchmark` Macrobenchmark module (cold startup, tab switch and Now Playing open with `FrameTimingMetric`) and a baseline profile generator.

## 8. Suggested fix order

One change at a time, each measured with the steps above. "ADR" marks an engineering decision to record.

| # | Fix | Findings | Symptoms | ADR |
|---|---|---|---|---|
| 1 | Measurable build type, probes, StrictMode in debug | B1, B2 | all | Yes: new build type; debug probes |
| 2 | Split position from queue state; tick only while observed; no DB in the tick; read position in the seek bar only | P1, P2, C1, C2 | P, T, B | Yes: `PlaybackController` contract |
| 3 | Map rows off the main thread (`flowOn` in the readers) | D1 to D4 | S, T | No, follows existing threading rule |
| 4 | Restore the queue off the main thread, ids only | S6, S5 | S, P | Small one, extends 0036 |
| 5 | Skip or rewrite `writeFeaturedArtists` (no change, no rewrite; sort once; map by first word) | S2, S1 | S | Yes: amends 0014's "cheap" assumption |
| 6 | One shared scoring result and one shared library flow per process; cards that show independently; longer lifetime for derived lists | H1, H2, H3, N1, M1 | S, T, B | Yes: shared board; supersede 0023 if a cached table is still needed |
| 7 | Store the display artist at scan time | D1 | S, T | Yes: schema v8, supersedes the "derived on read" part of 0026 |
| 8 | Delay and constrain the analysis worker | S3 | S, T, B | Yes: amends 0059 |
| 9 | Cover loader: de-duplicate, size buckets, cache sized to the device, initial value from cache | I1, I2, C3 | T | Yes if Coil is reconsidered (0016) |
| 10 | Short or no top-level transitions; stability config for `:core:model` | N2, B3 | T | Yes, small |
| 11 | Baseline profile and Macrobenchmark module | B1 | all | Yes |
| 12 | Low items: WorkManager init after first frame, blank loading shell, main-thread checkpoint writes, crossfade polling, 15 s queue rewrite, analysis aggregate, playlists join | S4, S7, D7, P4, P5, H4, H5 | S, B | Only where behaviour changes |

Steps 2, 3 and 4 are expected to address most of the progress-bar and Now Playing symptoms; steps 3, 4, 5 and 6 most of the start-up symptom; steps 3 and 6 most of the tab-switch stutter. These are expectations, to be checked against the numbers from sections 1 to 4.
