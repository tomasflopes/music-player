# Phase 1 (MP3 player that replaces Vinyl) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** phase 1 is built and accepted (2026-11-14), tagged `phase-1`. Task 31's phone measurements and `deviceCheck` run are not recorded. Decisions were approved by the owner on 2026-11-13, with three additions (section 0.0).

**Goal:** A polished, fully offline Android MP3 player the owner can use daily instead of Vinyl: folder-scoped library read from ID3 tags, albums in exact disc/track order, Folders/Artists/Albums/Songs, Recently added, better search with featured artists, plain shuffle with Play next / Add to queue, notification and car Bluetooth controls, and a listening log recorded from day one.

**Architecture:** Pure-Kotlin core modules hold every rule (tag parsing, album order, search, shuffle, dynamic lists); `:data` (Room, MediaStore scan, DataStore) and `:playback` (Media3 service) implement small interfaces defined in core; `:app` is Compose + ViewModels wired by Hilt multibindings. The player holds a short window of the queue while a pure-Kotlin `PlayQueue` owns the full order, so shuffle-all of 16k songs starts instantly and later mixes plug in as new queue sources.

**Tech stack (already pinned in `gradle/libs.versions.toml`):** Kotlin 2.4.21, AGP 9.5.1, Compose BOM 2026.09.00 (Material 3), Media3 1.11.1, Room 2.8.5, Hilt 2.60.1, WorkManager 2.12.0, DataStore 1.3.0, Coil 3.6.3, Navigation Compose 2.10.2, JUnit 6.1.3, Turbine 1.2.1, ktlint, detekt 2.0.0-alpha.6.

**Spec:** `design/2026-11-13-music-player-design.md` in the claude.ai project (phase 1 scope: §10; wins over the brief). How to build: Engineering Guidelines doc (https://claude.ai/artifact/LVxDCUR9B7pcfTBhmvrBva). History: `claude/PROJECT_BRIEF.md`.

**Path shorthand used below:** `{m}` = `src/main/kotlin/dev/tomasflopes/musicplayer`, `{t}` = `src/test/kotlin/dev/tomasflopes/musicplayer`, `{a}` = `src/androidTest/kotlin/dev/tomasflopes/musicplayer`, `{f}` = `src/testFixtures/kotlin/dev/tomasflopes/musicplayer`.

---

## 0. Decisions (all approved by the owner, 2026-11-13)

### 0.0 Added at approval

- **A1. Shuffle toggle.** Tapping a song plays its list in order (shuffle off). A shuffle button in Now Playing shuffles what is left of the queue without interrupting the current song; pressing it again puts the queue back in its original order, continuing from the current song. The Shuffle buttons on Home, artist, album, folder and Recently added start with shuffle on.
- **A2. Repeat.** Off → repeat all → repeat one, in Now Playing. The mode stays until changed. Replaces C6.
- **A3. Search ignores accents and symbols.** Names are stored and shown exactly as tagged (`JAY-Z`, `A$AP Rocky`, `m.A.A.d city`, `Song (Remix)`). Matching ignores case, accents and every symbol (`-`, `.`, `(`, `)`, …), so `jayz`, `jay z` and `jay-z` all find `JAY-Z`.

Three consequences of A1 to A3 that were not asked for explicitly (say if you disagree):

- **A4. `play_event` gets a `shuffle` column (0/1).** Design §7.4 scores a completed play "under shuffle"; with a toggle, `source` alone no longer says whether shuffle was on, and the log cannot be backfilled.
- **A5. `$` also matches as `s`,** so `asap rocky` finds `A$AP Rocky` (and `a$ap` still does).
- **A6. Shuffle and repeat commands from the car or a headset** go to the same queue logic, and the car is told the real state.

ADRs: every decision in this section is written as an ADR by the task that implements it (10 to 15 lines, format in guidelines §3.7) and added to `docs/adr/INDEX.md`. ADR numbers in this plan are indicative; use the next free number in the index.

### 0.1 Where the design doc and the guidelines disagree

| # | Design doc | Guidelines | What this plan does (approved) |
|---|---|---|---|
| D1 | §10 requires a **shuffle-strategy interface** and **Home sections as providers** from day one | §2.4 extension-point table has neither; it lists `SoundProfiler`, `SignalRule`, `CandidateSource`, `SuggestionFilter`, `WrappedCard`, which phase 1 never uses | Builds the six points of design §10 (`DynamicListProvider`, `ShuffleStrategy`, `SearchMatcher`, `HomeSection`, full `play_event`, Room v1 + migration harness). Does **not** create the five later-phase interfaces: an interface with no implementation and no caller would be guesswork. Suggest adding `ShuffleStrategy` and `HomeSection` rows to the guidelines table. |
| D2 | §10: playlists are out of phase 1 | §3.5 lists "add to playlist" as a critical UI test flow | UI tests cover onboarding and shuffle all only. |
| D3 | §10 has no debug screen in phase 1 | §3.7 wants a debug-only screen (analysis progress, exploration ratio, rebuild scores), none of which exists yet | Ships a small debug-build-only screen: library counts, last scan, Rescan, and the last 50 `play_event` rows, so the invisible log can be checked. |
| D4 | §12 module list: `:core:{model,taste,search,analysis}`, `:data`, `:playback`, `:app` | §2.1 same list; principle "pure logic, thin Android" | Adds one module, **`:core:tags`** (pure-Kotlin ID3 reader). Reason: it is the riskiest logic, needs fast JVM tests, and a JVM audit command must run it over your real library on the Mac, which a class inside the Android `:data` module cannot do. |
| D5 | n/a | §2.4 says a new implementation is "one class + one annotation"; §1 says core has no Android (and so no Hilt) | Core classes stay annotation-free. Registering = one class + one `@Binds/@Provides @IntoSet` line in a Hilt module in `:app`. `CONTRIBUTING.md` documents it that way. |
| D6 | §7.3: `play_event` is append-only, never deleted | §3.2: write the event "the moment a track ends or is skipped, so a crash never loses history" | A crash mid-song would still lose that song. The plan keeps a tiny "pending play" checkpoint outside the table and appends it as `stopped` on next start. The table itself stays insert-only. |

Precedence applied elsewhere: design wins over brief; guidelines win on code structure (as the design doc's header says).

### 0.2 Gaps in the design that affect the listening log (approved additions)

- [x] **G1. Missing `source` values.** The 13 values in design §7.3 have no value for "tapped a song in the Songs tab" or "tapped a song on an Artist page without shuffle". Proposal: add `songs` and `artist`. Without them these plays must be mislabelled.
- [x] **G2. "Tapped directly" is not recorded.** Design §7.4 gives +3 to a song "tapped directly", but `play_event` cannot tell the tapped song from the ones that auto-played after it. Proposal: add column `started_by` with `picked` (you tapped this song), `auto` (previous track ended), `nav` (next/previous button). Cheap now, impossible to backfill.
- [x] **G3. Search logging.** §7.3 says searches "that lead to a play" are logged; §7.9 says "choosing a result is logged". Plan logs every chosen result (artist, album or song), which covers both.

### 0.3 Choices (approved)

- [x] **C1. Hand-written ID3 reader**, no tag library. Validated three ways: byte-level unit tests, a mutation (fuzz) test, and an audit command run over your real library (Task 6).
- [x] **C2. Room, DAO and migration tests run on the emulator** (`./gradlew deviceCheck`), not in the pre-push hook. Guidelines §3.5 allow instrumented or Robolectric; I have not verified Robolectric on API 37, and real SQLite is the safer judge for the log. `./gradlew check` stays JVM-only and fast.
- [x] **C3. Windowed queue.** `PlayQueue` (pure Kotlin) keeps the whole order; ExoPlayer holds about 20 items each side of the current song. Keeps "Shuffle all starts under 500 ms" and is the shape mixes need later.
- [x] **C4. Songs are never deleted from the database**, only flagged `in_library = 0`, and are identified by volume + path with the MediaStore id as fallback. This is what makes "history comes back when the folder is changed back" (design §7.0) true.
- [x] **C5. Phase 1 navigation:** bottom tabs Home · Library · Search (Stats appears with Wrapped); Library tabs Artists | Albums | Folders | Songs (Playlists appears in phase 2); Settings behind a gear icon on Home.
- [x] **C6. Superseded by A2:** repeat modes are part of phase 1.
- [x] **C7. Manual queue survives a new play request:** tapping another song or album plays it now, then anything you queued, then the rest (Spotify behaviour).
- [x] **C8. Multi-valued artist frames** (ID3v2.4): first value is used, the audit reports how many files have more than one. You decide after seeing the count.

### 0.4 New dependencies (approved; downloaded by Gradle, nothing is installed on the Mac)

Versions are read from Maven metadata at execution time and pinned in the catalog, as the scaffold did. None is a network library.

| Dependency | Used for |
|---|---|
| `androidx.hilt:hilt-work`, `androidx.hilt:hilt-compiler` | inject the scan worker |
| Kotlin serialization plugin + `kotlinx-serialization-core` | type-safe Navigation Compose routes |
| `kotlinx-coroutines-guava` | await Media3's `ListenableFuture`s |
| `androidx.test:runner`, `androidx.test:core`, `androidx.test.ext:junit` | emulator tests (Room, scanner, playback) |
| Compose `ui-test-junit4`, `ui-test-manifest` (via BOM) | the two UI flow tests |
| Compose `foundation`, `animation` (via BOM, catalog aliases only) | lists, transitions |
| Gradle `java-test-fixtures` (built in, no download) | share the tag-fixture writer and sample generator |

### 0.5 Things outside the repo I will ask for when the plan reaches them

Installing debug builds on the emulator and on your phone (adb) · pushing the generated sample MP3s to the emulator · reading your music folder on the Mac for the tag audit (read-only) · a USB-debugging session with your phone (Tasks 2, 15, 31) · the car test (Task 15) · creating the GitHub remote (only when you say).

---

## Global Constraints

- `minSdk = targetSdk = compileSdk = 37`; JDK 21; package `dev.tomasflopes.musicplayer`. Already set in `build-logic`; do not add compatibility code.
- **No `INTERNET` permission in any merged manifest, no network library in the dependency tree** (Task 1 enforces both).
- MP3 only (`audio/mpeg`). Library = one root folder, recursive; nothing outside it appears.
- Metadata is stored and shown exactly as tagged: no case changes, no trimming beyond surrounding whitespace and trailing NULs, no "feat." rewriting, symbols kept. Only search matching ignores case, accents and symbols.
- Album order = disc number, then track number, exactly as tagged; filename order where numbers are missing.
- Artist = the artist tag (main artist) only. Featured artists come from the title, never create artist entries.
- `play_event` and `search_event` are insert-only. No `@Update`, no `@Delete`, no `fallbackToDestructiveMigration`, anywhere, ever.
- `:core:*` has no Android imports. `:app` talks to core interfaces, never to DAOs, MediaStore or Media3 types.
- New behaviour = new implementation registered with a Hilt multibinding; no `when` over implementations.
- Decision points (project `CLAUDE.md`): anything that talks to a phone API, or sits on a performance-critical path (scan, search, shuffle), is an interface injected with Hilt. When an alternative is added, the old implementation stays so both can be benchmarked.
- Every engineering decision gets an ADR in `docs/adr/` plus a line in `docs/adr/INDEX.md`. Accepted ADRs are never edited, only superseded.
- Every tunable number lives in a config data class (`LibraryConfig`, `SearchConfig`, `PlaybackConfig`), never inline.
- Dark background, orange accent, fixed. No dynamic colour, no light theme.
- Performance budget (real phone, 16k songs): cold start to Home < 1.5 s · search < 100 ms per keystroke · Shuffle all starts < 500 ms · no dropped frames fast-scrolling Songs.
- Every task: TDD (failing test first), `./gradlew ktlintFormat check` green before commit, one commit per task minimum. Branch per milestone (`phase1/m<N>-<name>`), merged to `main` when its checkpoint passes. No remote until the owner says.
- Versions only in `gradle/libs.versions.toml`.
- Ask before anything outside the repo (section 0.5).

## Review Focus

Inputs the spec implies but does not spell out, most likely first. Each has a pinned test in the task that owns the code.

1. **Tags written by real tools, not by our fixtures:** UTF-16 with BOM, ID3v2.4 frames with non-synchsafe sizes, `TRCK` as `8`, `08`, `8/14`, missing `TPOS`. Expected: same title/artist/album text as Windows Explorer shows, track 8, disc 1. (Tasks 4, 6)
2. **Half-numbered albums:** some tracks numbered, some not; duplicate track numbers with no disc tag. Expected: numbered tracks first in number order, the rest in natural filename order, same order every time. (Task 7)
3. **Files that move:** renamed, moved to another subfolder, music folder changed and changed back, MediaStore ids reassigned. Expected: the song keeps its id and its history; nothing is duplicated. (Task 11)
4. **Controls with nobody looking at the phone:** car "play" when the app was killed; ten quick "next" presses; a queued file that was deleted; headphones unplugged; shuffle toggled mid-song. Expected: it plays / lands on the right song / skips the missing file without stalling / pauses / the current song keeps playing without a glitch. (Tasks 13, 14, 15, 17)
5. **Names with symbols, accents and traps:** `A$AP Rocky`, `JAY-Z`, `m.A.A.d city`, `N.W.A`, `Beyoncé`, titles `Defeat` and `Left Behind`, `(feat. X) [Remix]`. Expected: found whether or not the symbols are typed (`jayz`, `jay z`, `maad city`, `nwa`, `asap`), shown exactly as tagged, and parsed as features only where a real `feat`/`ft` word is present. (Tasks 18, 19, 20)

---

## File structure (what exists after phase 1)

```
core/tags/      {m}/core/tags/      TagReader, Id3TagReader, Id3v2Header, Id3v2Frames, Id3TextDecoder, Id3v1Reader,
                                    NumberPair, CoverReader, RawTags, TagSource
                {f}/core/tags/      Id3TagWriter, SilentMp3, SampleLibrary, SampleLibraryGenerator
                {t}/core/tags/audit LibraryAudit (main, run by the auditLibrary task)
core/model/     {m}/core/model/     Ids, Song, Artist, Album, Folder, SongFeature, AlbumKey, AlbumOrder, NaturalOrder,
                                    DisplayFallbacks, SectionIndexer
                …/log/              PlaySource, EndedBy, StartedBy, PlayEvent, SearchEvent, SearchItemType
                …/ports/            SongReader, ArtistReader, AlbumReader, FolderReader, PlayHistoryWriter,
                                    PlayHistoryReader, SearchHistoryWriter, SettingsRepository, LibraryScanController
                …/lists/            DynamicListProvider
                …/config/           LibraryConfig
core/taste/     {m}/core/taste/shuffle/  ShuffleStrategy, PlainShuffle
                {m}/core/taste/lists/    RecentlyAddedProvider
core/search/    {m}/core/search/    TextNormalizer, FeaturedArtistParser, SearchMatcher, FieldMatcher, MatchTier,
                                    SearchDoc, SearchEngine, SearchIndex, SearchResults, SearchConfig, SearchTieBreaker
data/           {m}/data/db/        MusicDatabase, entities, DAOs, Migrations, Converters
                {m}/data/scan/      TreeUriMapper, SongFileLister, MediaStoreSongLister, FileTagSource, ScanPlanner, LibraryAssembler,
                                    LibraryScanner, LibraryScanWorker, MediaStoreObserver
                {m}/data/repo/      RoomSongReader, RoomArtistReader, RoomAlbumReader, RoomFolderReader,
                                    RoomPlayHistory, RoomSearchHistory
                {m}/data/settings/  DataStoreSettingsRepository
                {m}/data/di/        DataModule
                data/schemas/       exported Room schema JSON (committed)
playback/       {m}/playback/       PlaybackController, PlayRequest, PlaybackState, PlaybackConfig, PlaybackService,
                                    MediaControllerPlaybackController, MediaItemMapper
                …/queue/            PlayQueue, QueueEntry, RepeatMode, QueueWindowSync, QueueStateStore
                …/log/              PlayEventTracker, PlayEventRecorder, PendingPlayStore
                …/di/               PlaybackModule
app/            {m}/di/             ExtensionPointsModule, AppModule (clock, dispatchers, configs)
                {m}/navigation/     Routes, AppNavHost, BottomBar
                {m}/ui/theme/       Color, Type, Shape, Spacing, Theme
                {m}/ui/components/  SongRow, AlbumCard, ArtistRow, FolderRow, SectionHeader, AlbumHeader, CoverArt,
                                    FastScroller, MiniPlayer, PlayShuffleButtons, SongMenu, EmptyState
                {m}/ui/art/         EmbeddedCoverFetcher
                {m}/<feature>/      onboarding, home (+ HomeSection, sections/), library (songs, artists, albums,
                                    folders, album, artist), search, nowplaying, settings
                src/debug/…/debug/  DebugScreen, DebugViewModel
docs/adr/       INDEX.md, 0005 … 0013 (indicative numbers)
scripts/        push-samples.sh
```

---

## Milestone 0: guardrails

### Task 1: Android unit tests + the no-network guard

**Files:**
- Create: `build-logic/convention/src/main/kotlin/AndroidTestConventions.kt`, `build-logic/convention/src/main/kotlin/NoNetworkGuard.kt`
- Modify: `AndroidApplicationConventionPlugin.kt`, `AndroidLibraryConventionPlugin.kt` (call both)
- Test: `app/{t}/NoInternetPermissionTest.kt`

**Interfaces:**
- Produces: JUnit Platform unit tests in `:app`, `:data`, `:playback` (same test deps as `KotlinJvmConventionPlugin`); system property `musicplayer.mergedManifest` on `:app` unit-test tasks, pointing at `SingleArtifact.MERGED_MANIFEST` of the variant under test; task `verifyNoNetworkLibraries<Variant>` attached to `check`.

- [ ] **Step 1: Write the failing test**

```kotlin
class NoInternetPermissionTest {
    private val manifest = File(checkNotNull(System.getProperty("musicplayer.mergedManifest"))).readText()

    @Test
    fun `merged manifest never requests INTERNET`() {
        assertFalse(manifest.contains("android.permission.INTERNET"), "INTERNET permission found in merged manifest")
    }

    @Test
    fun `merged manifest is the real one`() {
        assertTrue(manifest.contains("dev.tomasflopes.musicplayer"))
    }
}
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest`. Expected: FAIL (property missing / no JUnit Platform).
- [ ] **Step 3:** Implement `configureAndroidUnitTests()` (JUnit Platform + test deps) and wire the merged manifest into the test task through the AGP Variant API (`androidComponents.onVariants`), not by task-name guessing.
- [ ] **Step 4:** Implement `verifyNoNetworkLibraries<Variant>`: resolve the variant runtime classpath and fail, naming the module, if any coordinate starts with `com.squareup.okhttp3`, `com.squareup.retrofit2`, `io.ktor`, `io.coil-kt.coil3:coil-network`, `com.android.volley`, `org.chromium.net`, `com.google.android.gms`, `com.google.firebase`.
- [ ] **Step 5: Prove both can fail.** Temporarily add `<uses-permission android:name="android.permission.INTERNET"/>` to `app/src/main/AndroidManifest.xml` → test FAILS. Remove it. Temporarily add `io.coil-kt.coil3:coil-network-okhttp` to `:app` → `check` FAILS. Remove it.
- [ ] **Step 6:** `./gradlew check` PASS. Commit: `build: unit tests on Android modules; guard against INTERNET and network libraries`.

---

## Milestone 1: storage and tags (the two riskiest unknowns)

### Task 2: Spike: folder picker → MediaStore (guidelines §4.3 #1)

Throwaway app code on branch `spike/folder-mediastore` (never merged). What is kept: `TreeUriMapper` with its tests, and ADR 0005.

**Files:**
- Create (kept, cherry-picked to the milestone branch): `data/{m}/data/scan/TreeUriMapper.kt`, `data/{t}/data/scan/TreeUriMapperTest.kt`, `docs/adr/0005-folder-picker-to-mediastore.md`
- Create (throwaway): `app/src/debug/kotlin/dev/tomasflopes/musicplayer/spike/FolderSpikeActivity.kt` + manifest entry, `READ_MEDIA_AUDIO` in the app manifest (this permission stays)

**Interfaces:**
- Produces: `data class MusicRootLocation(val volumeName: String, val relativePath: String)` (`relativePath` is `""` for a volume root, else ends with `/`); `object TreeUriMapper { fun fromTreeDocumentId(documentId: String): MusicRootLocation?; fun likePattern(location: MusicRootLocation): String }` (pattern for `RELATIVE_PATH LIKE ? ESCAPE '\'`).

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `primary subfolder`() =
    assertEquals(MusicRootLocation("external_primary", "Music/HipHop/"), TreeUriMapper.fromTreeDocumentId("primary:Music/HipHop"))
@Test fun `primary root`() =
    assertEquals(MusicRootLocation("external_primary", ""), TreeUriMapper.fromTreeDocumentId("primary:"))
@Test fun `sd card volume id is lower-cased`() =
    assertEquals(MusicRootLocation("1a2b-3c4d", "Music/"), TreeUriMapper.fromTreeDocumentId("1A2B-3C4D:Music"))
@Test fun `unknown id shape gives null`() = assertNull(TreeUriMapper.fromTreeDocumentId("downloads"))
@Test fun `like pattern escapes wildcards`() =
    assertEquals("Music/100\\% Rap\\_Mix/%", TreeUriMapper.likePattern(MusicRootLocation("external_primary", "Music/100% Rap_Mix/")))
```

- [ ] **Step 2:** Run `./gradlew :data:testDebugUnitTest --tests '*TreeUriMapperTest'` → FAIL. Implement. PASS. Commit.
- [ ] **Step 3:** Build the spike screen: request `READ_MEDIA_AUDIO`; `ACTION_OPEN_DOCUMENT_TREE`; reject any authority other than `com.android.externalstorage.documents`; query `MediaStore.Audio.Media` on the mapped volume with `MIME_TYPE = 'audio/mpeg' AND RELATIVE_PATH LIKE ?`; show row count, list time, and the 20 newest by `DATE_ADDED`; read the first 4 KB of 500 files both ways (file path, and `ContentResolver.openFileDescriptor`) and show both timings; register a `ContentObserver`.
- [ ] **Step 4 (owner + phone, USB debugging; ask first):** install on the phone, pick the real music folder, and fill ADR 0005 with measured answers:

| Q | Question | Pass |
|---|---|---|
| 1 | Does the tree id map to the right volume and prefix (also on SD card if you use one)? | folder shown = folder picked |
| 2 | MediaStore row count vs `adb shell "find '<root>' -iname '*.mp3' \| wc -l"` | equal, or every difference explained |
| 3 | Any WhatsApp audio, recording, ringtone or non-MP3 in the result? | none |
| 4 | Which read path is faster for tag bytes, and does the file-path one work at all on Android 17? | one path works under 3 ms/file |
| 5 | Do the 20 newest by `DATE_ADDED` match Vinyl's Recently added? | same songs |
| 6 | Copy one MP3 in: does the observer fire, and how late? | fires within ~1 min |
| 7 | `.nomedia` folders or files MediaStore has not indexed? | known and written down |
| 8 | Time to list the whole library | under 2 s |

- [ ] **Step 5: Decide and record.** Q1 to Q3 pass → SAF picker + `RELATIVE_PATH` prefix (the guidelines' leaning). Otherwise → walk the mapped path with `java.io.File` for the file list and keep MediaStore only for ids and `DATE_ADDED`; Task 12 then implements that lister instead. Write the decision, the numbers and the chosen read path into ADR 0005. Commit ADR + mapper on `phase1/m1-tags`.

### Task 3: `:core:tags` module, ID3v2 header and text decoding

**Files:**
- Create: `core/tags/build.gradle.kts` (`musicplayer.kotlin.jvm` + `java-test-fixtures`), entry in `settings.gradle.kts`, `core/tags/{m}/core/tags/{TagSource,RawTags,TagReader,Id3v2Header,Id3TextDecoder,NumberPair}.kt`
- Test: `core/tags/{t}/core/tags/{Id3v2HeaderTest,Id3TextDecoderTest,NumberPairTest}.kt`

**Interfaces (produces):**

```kotlin
interface TagSource { val fileName: String; fun openStream(): InputStream; fun readTail(byteCount: Int): ByteArray? }
enum class TagVersion { ID3V2_2, ID3V2_3, ID3V2_4, ID3V1, NONE }
data class RawTags(
    val title: String?, val artist: String?, val albumArtist: String?, val album: String?,
    val trackNumber: Int?, val trackTotal: Int?, val discNumber: Int?, val discTotal: Int?,
    val hasCover: Boolean, val version: TagVersion,
)
sealed interface TagReadResult {
    data class Success(val tags: RawTags, val warnings: List<String>) : TagReadResult
    data class Failure(val reason: String) : TagReadResult
}
interface TagReader { fun read(source: TagSource): TagReadResult }   // never throws
internal data class Id3v2Header(val major: Int, val unsynchronised: Boolean, val hasExtendedHeader: Boolean, val hasFooter: Boolean, val tagSize: Int)
internal object Id3TextDecoder { fun decode(encodingByte: Int, bytes: ByteArray): List<String> }  // split on NUL, trailing NULs dropped
internal object NumberPair { fun parse(text: String?): Pair<Int?, Int?> }
```

- [ ] **Step 1: Write the failing tests** (values are the spec's and the ID3 standard's):

```kotlin
@Test fun `synchsafe 00 00 02 01 is 257`() = assertEquals(257, synchsafe(0x00, 0x00, 0x02, 0x01))
@Test fun `header ID3 03 00 00 then size`() {
    val h = Id3v2Header.parse(bytes("49 44 33 03 00 00 00 00 02 01"))!!
    assertEquals(3, h.major); assertEquals(257, h.tagSize); assertFalse(h.unsynchronised)
}
@Test fun `not an ID3 header gives null`() = assertNull(Id3v2Header.parse(bytes("FF FB 90 64 00 00 00 00 00 00")))
@Test fun `major 5 or FF bytes in size are rejected`() { /* null for both */ }

@Test fun `latin1 e9 is e-acute`() = assertEquals(listOf("Beyoncé"), Id3TextDecoder.decode(0, bytes("42 65 79 6F 6E 63 E9")))
@Test fun `utf16 with LE BOM`() = assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("FF FE 45 00 64 00")))
@Test fun `utf16 with BE BOM`() = assertEquals(listOf("Ed"), Id3TextDecoder.decode(1, bytes("FE FF 00 45 00 64")))
@Test fun `utf16be without BOM`() = assertEquals(listOf("Ed"), Id3TextDecoder.decode(2, bytes("00 45 00 64")))
@Test fun `utf8`() = assertEquals(listOf("Beyoncé"), Id3TextDecoder.decode(3, "Beyoncé".toByteArray()))
@Test fun `trailing NUL dropped, inner NUL splits`() = assertEquals(listOf("A", "B"), Id3TextDecoder.decode(3, bytes("41 00 42 00")))
@Test fun `unknown encoding byte falls back to latin1`() { /* decode(9, …) == decode(0, …) */ }

@ParameterizedTest @CsvSource("8,8,", "08,8,", "8/14,8,14", "' 8 ',8,", "8/,8,", "0,,", "'',,", "A1,,", "/14,,14")
fun `number pair`(text: String, number: Int?, total: Int?) = assertEquals(number to total, NumberPair.parse(text))
```

- [ ] **Step 2:** `./gradlew :core:tags:test` → FAIL. Implement. PASS. `./gradlew check`. Commit: `feat(tags): ID3v2 header, text decoding, number pairs`.

Decision pinned by the tests: a track or disc number of `0` means "not tagged" (null).

### Task 4: ID3 frames → `RawTags`, with ID3v1 fallback

**Files:**
- Create: `core/tags/{m}/core/tags/{Id3TagReader,Id3v2Frames,Id3v1Reader}.kt`; `core/tags/{f}/core/tags/Id3TagWriter.kt` (fixture DSL: `id3v22 { }`, `id3v23 { }`, `id3v24 { }` with `text(id, value, encoding)`, `picture(...)`, `unsynchronised()`, `extendedHeader()`, `padding(n)`, `rawFrame(...)`)
- Test: `core/tags/{t}/core/tags/{Id3TagReaderTest,Id3v1ReaderTest,Id3RobustnessTest}.kt`

**Interfaces:**
- Consumes: Task 3 types.
- Produces: `class Id3TagReader : TagReader`. Frame map: title `TIT2`/`TT2`, artist `TPE1`/`TP1`, album artist `TPE2`/`TP2`, album `TALB`/`TAL`, track `TRCK`/`TRK`, disc `TPOS`/`TPA`, picture `APIC`/`PIC`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `v2_3 utf16 album track`() {
    val tags = read(id3v23 {
        text("TIT2", "HUMBLE.", UTF16); text("TPE1", "Kendrick Lamar", UTF16); text("TPE2", "Kendrick Lamar", UTF16)
        text("TALB", "DAMN.", UTF16); text("TRCK", "8/14"); text("TPOS", "1/1")
    })
    assertEquals(RawTags("HUMBLE.", "Kendrick Lamar", "Kendrick Lamar", "DAMN.", 8, 14, 1, 1, false, TagVersion.ID3V2_3), tags)
}
@Test fun `v2_4 utf8 with synchsafe frame sizes`() { /* same values, ID3V2_4 */ }
@Test fun `v2_4 written with plain 32-bit frame sizes still reads`() { /* a 300-byte TIT2 written non-synchsafe: title intact, next frame (TRCK=3) read */ }
@Test fun `v2_2 three-letter frames`() { /* TT2 TP1 TAL TRK=3 TPA=2 → track 3, disc 2, ID3V2_2 */ }
@Test fun `tag-level unsynchronisation is undone`() { /* title bytes containing FF 00 round-trip */ }
@Test fun `extended header and padding are skipped`() { }
@Test fun `picture sets hasCover and its bytes are not read into memory`() { /* 5 MB APIC, reader's max single allocation < 64 KB */ }
@Test fun `compressed or encrypted frame is skipped with a warning`() { }
@Test fun `text is kept exactly`() { /* "  DAMN. " → "DAMN." (outer whitespace only); "damn." stays lower case; "A/B" stays "A/B" */ }
@Test fun `multi-value artist takes the first and warns`() { /* v2.4 "Ed\u0000SZA" → artist "Ed", warning "multi-value TPE1" */ }
@Test fun `missing frames are null, not empty`() { }
@Test fun `no v2 tag falls back to ID3v1_1`() { /* title/artist/album 30 bytes latin1 trimmed, track byte 5 → 5, ID3V1 */ }
@Test fun `no tag at all`() = assertEquals(TagVersion.NONE, read(SilentMp3.frames(10)).version)
@Test fun `tag size larger than the file is a Failure, not a crash`() { }
@Test fun `1000 random truncations and byte flips never throw`() { /* seeded Random(37); every result is Success or Failure */ }
```

- [ ] **Step 2:** `./gradlew :core:tags:test` → FAIL. Implement the reader (sequential read; `skip` over picture payloads; when a v2.4 frame size read as synchsafe does not land on a valid frame id or padding but the plain reading does, use the plain one). PASS.
- [ ] **Step 3:** `./gradlew check`. Commit: `feat(tags): read ID3v2.2/2.3/2.4 and ID3v1 into RawTags`. Add `docs/adr/0006-hand-written-id3-reader.md` (context, decision, the three validation layers).

### Task 5: Cover extraction, sample MP3 set, fake-library generator

**Files:**
- Create: `core/tags/{m}/core/tags/CoverReader.kt`; `core/tags/{f}/core/tags/{SilentMp3,SampleLibrary,SampleLibraryGenerator}.kt`; `scripts/push-samples.sh`; task `generateSamples` in `core/tags/build.gradle.kts`
- Test: `core/tags/{t}/core/tags/{CoverReaderTest,SampleLibraryTest}.kt`

**Interfaces (produces):**

```kotlin
class Cover(val bytes: ByteArray, val mimeType: String)
interface CoverReader { fun readCover(source: TagSource): Cover? }   // front cover (type 3) if present, else first picture
object SilentMp3 { fun frames(seconds: Int): ByteArray }              // valid MPEG-1 Layer III silent frames, no tag
data class SampleFile(val path: String, val expected: RawTags, val inRoot: Boolean)
object SampleLibrary { val files: List<SampleFile>; const val ROOT = "Music/Sample" }
object SampleLibraryGenerator { fun writeTo(dir: File) }
```

`SampleLibrary` contents (about 30 files, 10 s each, all generated, nothing downloaded):
- `Album One` by `Sample A`: 2 discs × 3 tracks, v2.3 UTF-16, file names in an order different from track order.
- `Album Two` by `Sample B`: v2.4 UTF-8, no track numbers, files `1 - x.mp3`, `2 - y.mp3`, `10 - z.mp3`.
- `Same Name` by album artist `Sample A` and again by `Sample B` (grouping).
- Artist `Ed` with 2 songs; songs titled `Faded` and `Ended` by `Sample B`; titles using `feat. Ed Sample A`, `ft. Ed`, `feat Sample B`, `(feat. Ed) [Remix]`, `Defeat`.
- One v2.2 file, one ID3v1-only file, one untagged file, two files with an embedded 64×64 orange PNG.
- Names with accents and symbols, to check display and search by hand: artist `S.A.M-Ple`, artist `Sam$on Café`, song `m.A.A.d sample (Remix)`, album `LOUD.`.
- Outside the root (`inRoot = false`): `Recordings/voice note.mp3`.

- [ ] **Step 1:** Failing tests: `readCover` returns the type-3 picture when two pictures exist; returns null without one; `SampleLibraryTest` writes the set to a temp dir and asserts `Id3TagReader` returns exactly `expected` for every file.
- [ ] **Step 2:** Implement. `./gradlew :core:tags:test generateSamples` → PASS and files under `core/tags/build/samples/`.
- [ ] **Step 3:** `scripts/push-samples.sh`: runs `generateSamples`, `adb push`es to `/sdcard/`, triggers a media scan. **Ask before first run** (touches the emulator). Verify one file plays in the emulator's Files app; if the silent frames do not decode, fix `SilentMp3` here, not later.
- [ ] **Step 4:** Commit: `feat(tags): cover reader, generated sample library, push script`.

### Task 6: Audit the real library on the Mac (owner checkpoint)

**Files:**
- Create: `core/tags/{t}/core/tags/audit/LibraryAudit.kt` (`main`), `JavaExec` task `auditLibrary` in `core/tags/build.gradle.kts`

**Interfaces:**
- Consumes: `Id3TagReader`, and `AlbumKey` / `AlbumOrder` rules. To avoid a dependency on Task 7, the audit groups and orders with the same two functions once Task 7 lands; until then it reports per-file data only. Run the album part after Task 7.
- Produces: `./gradlew :core:tags:auditLibrary -Proot=<music folder> [-Palbums="DAMN.,…"]` writing `core/tags/build/tag-audit/{summary.txt,files.csv,albums.csv}` (build dir, never committed).

`summary.txt` reports: files read · files/second · count per `TagVersion` · failures (each listed) · warnings by kind · files missing title / artist / album · files with multi-valued artist (C8) · albums fully numbered / partly numbered / unnumbered · albums with duplicate (disc, track) · for each album named in `-Palbums`, the track list in computed order.

- [ ] **Step 1:** Unit-test the report builder on the sample set (counts equal `SampleLibrary`'s known mix). Implement.
- [ ] **Step 2 (ask first: reads the music folder, read-only):** owner runs it on the real library.
- [ ] **Step 3: Checkpoint.** Pass when: zero failures or each one explained; owner confirms the printed order for the albums he plays most; throughput suggests a full phone scan in minutes. Any tag shape the reader mishandles becomes a new test in Task 4 first, then a fix.
- [ ] **Step 4 (optional, ask):** export tag-only bytes (text frames, pictures dropped) of about 10 real files into `core/tags/src/test/resources/real/` as regression fixtures. Commit: `test(tags): library audit command`.

---

## Milestone 2: domain and data

### Task 7: Domain model, album grouping and order

**Files:**
- Create: `core/model/{m}/core/model/{Ids,Song,Artist,Album,Folder,SongFeature,AlbumKey,AlbumOrder,NaturalOrder,DisplayFallbacks,SectionIndexer}.kt`; add `kotlinx-coroutines-core` to `core/model/build.gradle.kts`
- Delete: `core/model/{t}/core/model/PipelineSmokeTest.kt` (in the same commit as the first real test)
- Test: `core/model/{t}/core/model/{AlbumOrderTest,AlbumKeyTest,NaturalOrderTest,DisplayFallbacksTest,SectionIndexerTest}.kt`

**Interfaces (produces):**

```kotlin
@JvmInline value class SongId(val value: Long); @JvmInline value class ArtistId(val value: Long); @JvmInline value class AlbumId(val value: Long)
data class Song(
    val id: SongId, val title: String, val artistId: ArtistId, val artistName: String,
    val albumId: AlbumId?, val albumTitle: String?, val albumArtist: String?,
    val trackNumber: Int?, val discNumber: Int?, val durationMs: Long,
    val volumeName: String, val path: String,          // path on the volume, e.g. "Music/HipHop/Ed/01 x.mp3"
    val fileName: String, val folderPath: String, val dateAddedEpochSec: Long, val contentUri: String, val hasCover: Boolean,
)
data class SongRef(val songId: SongId, val mainArtistId: ArtistId)   // what queues and shuffles carry; `val Song.ref: SongRef`
data class Artist(val id: ArtistId, val name: String, val songCount: Int, val albumCount: Int)
data class Album(val id: AlbumId, val title: String, val albumArtist: String, val songCount: Int, val coverSongId: SongId?)
data class Folder(val path: String, val name: String, val parentPath: String?, val songCount: Int)
data class SongFeature(val songId: SongId, val artistId: ArtistId?, val rawName: String?)
data class AlbumKey(val title: String, val albumArtist: String) { companion object { fun of(album: String?, albumArtist: String?, artist: String?): AlbumKey? } }
object AlbumOrder : Comparator<Song>
object NaturalOrder : Comparator<String>               // case-insensitive, digit runs compared as numbers
object DisplayFallbacks { fun title(tagTitle: String?, fileName: String): String; fun artist(tagArtist: String?): String; const val UNKNOWN_ARTIST = "Unknown artist" }
data class Section(val label: Char, val firstIndex: Int)
object SectionIndexer { fun sections(sortKeys: List<String>): List<Section> }  // A–Z, '#' for the rest
```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `disc then track`() = assertOrder(listOf("c", "b", "a"), s("a", disc = 2, track = 1), s("b", disc = 1, track = 2), s("c", disc = 1, track = 1))
@Test fun `missing disc counts as disc 1`() = assertOrder(listOf("a", "b"), s("b", disc = 2, track = 1), s("a", disc = null, track = 9))
@Test fun `no numbers falls back to natural filename order`() =
    assertOrder(listOf("1 - x.mp3", "2 - y.mp3", "10 - z.mp3"), f("10 - z.mp3"), f("2 - y.mp3"), f("1 - x.mp3"))
@Test fun `numbered tracks come before unnumbered ones`() { }
@Test fun `duplicate track numbers order by filename and are stable across shuffled input`() { }

@Test fun `same album title, different album artists, are two albums`() =
    assertNotEquals(AlbumKey.of("Same Name", "Sample A", "x"), AlbumKey.of("Same Name", "Sample B", "x"))
@Test fun `blank album artist falls back to artist`() = assertEquals(AlbumKey("DAMN.", "Kendrick Lamar"), AlbumKey.of("DAMN.", " ", "Kendrick Lamar"))
@Test fun `blank album is no album`() = assertNull(AlbumKey.of("  ", "A", "A"))
@Test fun `key is case-sensitive, as tagged`() = assertNotEquals(AlbumKey.of("DAMN.", "K", null), AlbumKey.of("Damn.", "K", null))

@Test fun `untitled song shows its file name without extension`() = assertEquals("01 intro", DisplayFallbacks.title(null, "01 intro.mp3"))
@Test fun sections() = assertEquals(listOf(Section('#', 0), Section('A', 1), Section('B', 2), Section('Z', 4)),
    SectionIndexer.sections(listOf("2pac", "album", "beta", "beta 2", "zed")))
```

- [ ] **Step 2:** `./gradlew :core:model:test` → FAIL. Implement. PASS. Re-run Task 6's album report. Commit: `feat(model): domain types, album grouping and order`.

### Task 8: Log types, ports and the first two extension points

**Files:**
- Create: `core/model/{m}/core/model/log/{PlaySource,EndedBy,StartedBy,PlayEvent,SearchEvent,SearchItemType}.kt`; `…/ports/*.kt`; `…/lists/DynamicListProvider.kt`; `…/config/LibraryConfig.kt`; `core/taste/{m}/core/taste/shuffle/{ShuffleStrategy,PlainShuffle}.kt`; `core/taste/{m}/core/taste/lists/RecentlyAddedProvider.kt`
- Test: `core/model/{t}/core/model/log/PlaySourceTest.kt`; `core/taste/{t}/core/taste/shuffle/{ShuffleStrategyContract,PlainShuffleTest}.kt`; `core/taste/{t}/core/taste/lists/{DynamicListProviderContract,RecentlyAddedProviderTest}.kt`

**Interfaces (produces):**

```kotlin
enum class PlaySource(val wire: String) { SHUFFLE_ALL("shuffle_all"), ARTIST_SHUFFLE("artist_shuffle"), SEARCH("search"), ALBUM("album"),
    FOLDER("folder"), RECENTLY_ADDED("recently_added"), MIX("mix"), RADIO("radio"), DISCOVER("discover"), REWIND("rewind"),
    TOP100("top100"), PLAYLIST("playlist"), QUEUE("queue"), SONGS("songs"), ARTIST("artist");                       // last two: G1
    fun withShuffle(on: Boolean): PlaySource }   // ARTIST ↔ ARTIST_SHUFFLE, SONGS ↔ SHUFFLE_ALL; every other value returns itself
enum class EndedBy(val wire: String) { COMPLETED("completed"), SKIPPED("skipped"), STOPPED("stopped") }
enum class StartedBy(val wire: String) { PICKED("picked"), AUTO("auto"), NAV("nav") }      // G2
data class PlayEvent(val songId: SongId, val startedAtEpochMs: Long, val listenedMs: Long, val songDurationMs: Long,
    val endedBy: EndedBy, val source: PlaySource, val startedBy: StartedBy, val shuffle: Boolean,
    val mixId: Long? = null, val playlistId: Long? = null)
enum class SearchItemType { ARTIST, ALBUM, SONG }
data class SearchEvent(val query: String, val chosenType: SearchItemType, val chosenId: Long, val atEpochMs: Long)

interface SongReader {
    fun observeSongs(): Flow<List<Song>>                          // by sort title
    fun observeAddedSince(epochSec: Long): Flow<List<Song>>       // newest first
    fun observeAlbumSongs(id: AlbumId): Flow<List<Song>>          // in AlbumOrder
    fun observeArtistSongs(id: ArtistId): Flow<List<Song>>
    fun observeFeaturedOn(id: ArtistId): Flow<List<Song>>
    fun observeFolderSongs(folderPath: String): Flow<List<Song>>  // direct children, NaturalOrder by file name
    suspend fun songsByIds(ids: List<SongId>): List<Song>         // same order as ids
    suspend fun allSongRefs(): List<SongRef>
}
interface ArtistReader { fun observeArtists(): Flow<List<Artist>>; fun observeArtist(id: ArtistId): Flow<Artist?> }
interface AlbumReader { fun observeAlbums(): Flow<List<Album>>; fun observeAlbum(id: AlbumId): Flow<Album?>; fun observeArtistAlbums(id: ArtistId): Flow<List<Album>> }
interface FolderReader { fun observeChildren(parentPath: String?): Flow<List<Folder>> }       // null = music root
interface PlayHistoryWriter { suspend fun append(event: PlayEvent) }
interface PlayHistoryReader { fun observeRecent(limit: Int): Flow<List<PlayEvent>>; fun observeCount(): Flow<Int> }
interface SearchHistoryWriter { suspend fun append(event: SearchEvent) }
data class MusicRoot(val treeUri: String, val volumeName: String, val relativePath: String)
interface SettingsRepository { val musicRoot: Flow<MusicRoot?>; val recentlyAddedDays: Flow<Int>
    suspend fun setMusicRoot(root: MusicRoot); suspend fun setRecentlyAddedDays(days: Int) }
enum class ScanPhase { IDLE, LISTING, READING_TAGS, INDEXING, DONE, FAILED }
data class ScanProgress(val phase: ScanPhase, val done: Int, val total: Int)
interface LibraryScanController { val progress: StateFlow<ScanProgress>; fun requestScan() }

interface DynamicListProvider { val id: String; val title: String; val order: Int; val playSource: PlaySource; fun songs(): Flow<List<Song>> }
data class LibraryConfig(val recentlyAddedDefaultDays: Int = 20, val recentlyAddedQuickOptions: List<Int> = listOf(7, 15, 20, 30, 60, 90),
    val recentlyAddedDaysRange: IntRange = 1..3650, val scanBatchSize: Int = 500)

fun interface ShuffleStrategy { fun order(items: List<SongRef>, random: Random): List<SongRef> }
class PlainShuffle : ShuffleStrategy                                                           // Fisher–Yates
class RecentlyAddedProvider(songs: SongReader, settings: SettingsRepository, clock: java.time.Clock) : DynamicListProvider
    // id = "recently_added", title = "Recently added", order = 0, playSource = RECENTLY_ADDED
```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `source wire values are the design's 13 plus songs and artist`() = assertEquals(
    setOf("shuffle_all", "artist_shuffle", "search", "album", "folder", "recently_added", "mix", "radio", "discover", "rewind", "top100", "playlist", "queue", "songs", "artist"),
    PlaySource.entries.map { it.wire }.toSet())
@Test fun `shuffle relabels only the two paired sources`() { assertEquals(ARTIST_SHUFFLE, ARTIST.withShuffle(true)); assertEquals(ARTIST, ARTIST_SHUFFLE.withShuffle(false))
    assertEquals(SHUFFLE_ALL, SONGS.withShuffle(true)); assertEquals(SONGS, SHUFFLE_ALL.withShuffle(false)); assertEquals(ALBUM, ALBUM.withShuffle(true)) }

abstract class ShuffleStrategyContract { abstract fun strategy(): ShuffleStrategy
    @Test fun `returns every song exactly once`() { } ; @Test fun `empty and single lists`() { }
    @Test fun `same seed, same order`() { } ; @Test fun `16000 items under 50 ms`() { } }
class PlainShuffleTest : ShuffleStrategyContract() { @Test fun `seed 37 does not return the input order for 100 items`() { } }

abstract class DynamicListProviderContract { /* id non-blank and stable; songs() emits at least once; no duplicates */ }
class RecentlyAddedProviderTest : DynamicListProviderContract() {
    @Test fun `window is days times 86400 seconds back from now`() { /* now = 2026-11-13T12:00Z, days = 20 → observeAddedSince(now − 1_728_000) */ }
    @Test fun `changing days from 20 to 15 re-emits`() { /* Turbine: two emissions, second cutoff = now − 1_296_000 */ }
    @Test fun `default comes from LibraryConfig and is 20`() { }
}
```

- [ ] **Step 2:** `./gradlew :core:model:test :core:taste:test` → FAIL. Implement. PASS. Write the ADR "listening-log fields beyond the design" (`songs` / `artist` sources, `started_by`, `shuffle`; G1, G2, A4). Commit: `feat(core): log types, ports, DynamicListProvider, ShuffleStrategy`.

### Task 9: Room schema v1, DAOs, migration harness

**Files:**
- Create: `data/{m}/data/db/{MusicDatabase,Migrations,Converters}.kt`, `…/db/entity/{Song,Artist,Album,Folder,SongFeature,PlayEvent,SearchEvent}Entity.kt`, `…/db/dao/{Song,Artist,Album,Folder,SongFeature,PlayEvent,SearchEvent}Dao.kt`, `data/schemas/…/1.json` (generated, committed)
- Modify: `data/build.gradle.kts` (Hilt + KSP arg `room.schemaLocation`, `room-testing`, androidTest deps); `AndroidLibraryConventionPlugin.kt` (`testInstrumentationRunner`); root `build.gradle.kts` (task `deviceCheck` = every `connectedDebugAndroidTest`); `README.md` (document `deviceCheck`)
- Test: `data/{a}/data/db/{MusicDatabaseMigrationTest,SongDaoTest,PlayEventDaoTest}.kt`; `data/{t}/data/db/LogIsAppendOnlyTest.kt`

**Schema v1** (snake_case columns):
- `song`: `id` PK auto, `media_store_id`, `volume_name`, `path` (unique with `volume_name`), `file_name`, `folder_path`, `title`, `sort_title`, `artist_id`, `album_id?`, `album_artist?`, `track_number?`, `disc_number?`, `duration_ms`, `file_size`, `file_modified`, `date_added`, `has_cover`, `tag_version`, `in_library`. Indexes: `artist_id`, `album_id`, `folder_path`, `date_added`, `sort_title`.
- `artist` (`id`, `name` unique, `sort_name`) · `album` (`id`, `title`, `album_artist`, unique pair, `sort_title`) · `folder` (`path` PK, `name`, `parent_path?` indexed) · `song_feature` (`song_id` indexed, `artist_id?` indexed, `raw_name?`).
- `play_event`: `id` PK auto, `song_id`, `started_at`, `listened_ms`, `song_duration_ms`, `ended_by`, `source`, `started_by`, `shuffle`, `mix_id?`, `playlist_id?`. Indexes: `song_id`, `started_at`. No foreign key (nothing may cascade into the log).
- `search_event`: `id`, `query`, `chosen_type`, `chosen_id`, `at`.

**Interfaces (produces):** `MusicDatabase` (version 1, `exportSchema = true`); `Migrations.ALL: Array<Migration>` (empty at v1); `PlayEventDao { @Insert suspend fun insert(e); fun observeRecent(limit); fun observeCount() }`; `SearchEventDao { @Insert suspend fun insert(e) }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// androidTest
@Test fun createsV1AndOpensWithRealBuilder() { helper.createDatabase(DB, 1).close()
    Room.databaseBuilder(ctx, MusicDatabase::class.java, DB).addMigrations(*Migrations.ALL).build().openHelper.writableDatabase.close() }
@Test fun everyGuidelineIndexExists() { /* PRAGMA index_list: song(artist_id, album_id, folder_path, date_added), play_event(song_id, started_at) */ }
@Test fun albumSongsComeBackInDiscThenTrackOrder() { /* 2 discs × 3 tracks inserted shuffled → ids in order */ }
@Test fun addedSinceIsInclusiveAndNewestFirst() { }
@Test fun retiredSongsAreHiddenFromEveryReader() { /* in_library = 0 */ }
@Test fun everyLogValueRoundTrips() { /* one row per PlaySource × EndedBy × StartedBy × shuffle, read back equal */ }

// JVM unit test: scans data/src/main for forbidden text
@Test fun `log tables are insert-only and migrations are never destructive`() {
    assertNoMatch(Regex("fallbackToDestructiveMigration"), allMainSources)
    assertNoMatch(Regex("@(Update|Delete|Upsert)"), sourcesOf("PlayEventDao.kt", "SearchEventDao.kt"))
    assertNoMatch(Regex("(DELETE FROM|UPDATE)\\s+(play_event|search_event)", RegexOption.IGNORE_CASE), allMainSources)
}
```

- [ ] **Step 2:** Run `./gradlew :data:testDebugUnitTest` and (emulator running; **ask before first run**) `./gradlew :data:connectedDebugAndroidTest` → FAIL. Implement. PASS.
- [ ] **Step 3:** Add to `CONTRIBUTING.md`: "Changing the schema: bump version, add `Migration(n, n+1)` to `Migrations.ALL`, commit the new schema JSON, add a `migrateNToN+1` test that inserts a `play_event` row before and finds it after." Add `docs/adr/0007-room-tests-on-the-emulator.md`. Commit: `feat(data): Room schema v1 with migration harness`.

### Task 10: Settings

**Files:** Create `data/{m}/data/settings/DataStoreSettingsRepository.kt`, `data/{m}/data/di/DataModule.kt` · Test `data/{a}/data/settings/DataStoreSettingsRepositoryTest.kt`

**Interfaces:** Consumes `SettingsRepository`, `LibraryConfig`. Produces the Hilt binding. Keys: `music_root_tree_uri`, `music_root_volume`, `music_root_relative_path`, `recently_added_days`.

- [ ] Tests: default days = 20; `setRecentlyAddedDays(15)` then a new instance reads 15; values outside `1..3650` are coerced into range; `musicRoot` is null until set, then round-trips.
- [ ] Run, implement, pass, commit: `feat(data): settings in DataStore`.

### Task 11: Scan planning and library assembly (pure Kotlin)

**Files:** Create `data/{m}/data/scan/{ScanPlanner,LibraryAssembler}.kt` · Test `data/{t}/data/scan/{ScanPlannerTest,LibraryAssemblerTest}.kt`

**Interfaces (produces):**

```kotlin
data class MediaFile(val mediaStoreId: Long, val volumeName: String, val path: String, val sizeBytes: Long,
    val modifiedEpochSec: Long, val dateAddedEpochSec: Long, val durationMs: Long, val contentUri: String)
data class KnownSong(val id: SongId, val mediaStoreId: Long, val volumeName: String, val path: String,
    val sizeBytes: Long, val modifiedEpochSec: Long, val inLibrary: Boolean)
data class ScanPlan(val insert: List<MediaFile>, val rereadTags: List<Pair<SongId, MediaFile>>, val move: List<Pair<SongId, MediaFile>>,
    val revive: List<Pair<SongId, MediaFile>>, val retire: List<SongId>, val unchanged: Int)
object ScanPlanner { fun plan(found: List<MediaFile>, known: List<KnownSong>): ScanPlan }
data class AssembledSong(val file: MediaFile, val title: String, val artistName: String, val albumKey: AlbumKey?,
    val trackNumber: Int?, val discNumber: Int?, val hasCover: Boolean, val tagVersion: TagVersion, val folderPath: String, val sortTitle: String)
object LibraryAssembler { fun assemble(file: MediaFile, tags: TagReadResult): AssembledSong; fun folderChain(folderPath: String, rootPath: String): List<Folder> }
```

- [ ] Tests (Review Focus 3): new file → `insert` · same path/size/mtime → `unchanged`, no tag read · size or mtime changed → `rereadTags` · path gone, same `mediaStoreId` at a new path → `move` with the same `SongId` · path same, `mediaStoreId` changed → `unchanged` and id refreshed · file gone → `retire`, never delete · retired file found again → `revive` with the same `SongId` · root changed to a sibling and back → every original `SongId` revived. Assembler: tag `Failure` → title from file name, artist `Unknown artist`, no album; `date_added` is taken at insert and never overwritten by `move` / `revive` / `rereadTags`; `folderChain("Music/HipHop/Ed/Album", "Music/HipHop/")` → `Ed`, `Ed/Album` with correct parents.
- [ ] Run, implement, pass, commit: `feat(data): scan planning that keeps song ids stable`.

### Task 12: MediaStore lister, scanner, worker, observer

**Files:** Create `data/{m}/data/scan/{MediaStoreSongLister,FileTagSource,LibraryScanner,LibraryScanWorker,MediaStoreObserver}.kt`, `data/{m}/data/repo/Room*.kt`; modify `data/build.gradle.kts` (`:core:tags`, `hilt-work`), `data/src/main/AndroidManifest.xml` · Test `data/{a}/data/scan/LibraryScannerTest.kt` (needs the sample set on the emulator)

**Interfaces:**
- Consumes: `TreeUriMapper`, ADR 0005's chosen list and read paths, `ScanPlanner`, `LibraryAssembler`, `TagReader`, DAOs, `LibraryConfig.scanBatchSize`.
- Produces: `interface SongFileLister { suspend fun list(root: MusicRoot): List<MediaFile> }` with `MediaStoreSongLister` as the bound implementation (if ADR 0005 chose the file walk, `FileWalkSongLister` is added beside it and both stay, for benchmarking); `class LibraryScanner { suspend fun scan(onProgress: (ScanProgress) -> Unit) }`; `LibraryScanController` implementation backed by unique work `library-scan` (policy KEEP); rescan on app start, on MediaStore change (debounced 5 s) and on music-root change; the `Room*Reader` implementations of Task 8's ports.

- [ ] Tests (emulator): after a scan of `SampleLibrary.ROOT`, song count = in-root files; `Recordings/voice note.mp3` absent; every song's title/artist/album/track/disc equals `SampleLibrary.expected`; `Album One` reads back in disc → track order; `Same Name` is two albums; a second scan performs zero tag reads; deleting one file then scanning retires exactly one song and keeps its `play_event` rows; a file whose tags fail still appears, titled by file name; batches are 500 rows per transaction.
- [ ] Run (`:data:connectedDebugAndroidTest`), implement, pass. Record scan time for the sample set. Commit: `feat(data): library scan from the chosen folder`.

---

## Milestone 3: playback (the daily basics)

### Task 13: `PlayQueue` with play-next, shuffle toggle and repeat (pure Kotlin)

**Files:** Create `playback/{m}/playback/queue/{QueueEntry,RepeatMode,PlayQueue}.kt`, `playback/{m}/playback/PlaybackConfig.kt`; modify `playback/build.gradle.kts` (`:core:taste`) · Test `playback/{t}/playback/queue/{PlayQueueTest,PlayQueueShuffleTest,PlayQueueRepeatTest}.kt`

**Interfaces (produces):**

```kotlin
data class QueueEntry(val ref: SongRef, val source: PlaySource, val manual: Boolean)
enum class RepeatMode { OFF, ALL, ONE }
data class PlaybackConfig(val windowAhead: Int = 20, val windowBehind: Int = 20, val pendingCheckpointMs: Long = 10_000, val queueSaveDebounceMs: Long = 1_000)
class PlayQueue(private val strategy: ShuffleStrategy, private val random: Random) {
    val current: QueueEntry?; val index: Int; val size: Int
    val shuffled: Boolean; var repeatMode: RepeatMode
    fun replace(songs: List<SongRef>, startIndex: Int, source: PlaySource, shuffled: Boolean)  // keeps pending manual entries after the new current (C7)
    fun setShuffled(on: Boolean)                               // A1
    fun playNext(songs: List<SongRef>); fun addToQueue(songs: List<SongRef>)   // source = QUEUE, manual = true
    fun next(): QueueEntry?                                    // user pressed next
    fun advanceOnTrackEnd(): QueueEntry?                       // current song finished by itself
    fun previous(): QueueEntry?; fun jumpTo(index: Int): QueueEntry?
    fun window(behind: Int, ahead: Int): List<QueueEntry>; fun removeAt(index: Int)
    fun snapshot(): QueueSnapshot                              // order, original order, index, shuffled, repeatMode
}
```

Rules:
- `replace(..., shuffled = true)` puts `songs[startIndex]` first and orders the rest with the strategy. `shuffled = false` keeps list order.
- `setShuffled(true)`: the current entry and the manual entries stay where they are; the non-manual entries after the current one are re-ordered by the strategy; entries already played are untouched. `setShuffled(false)`: non-manual entries return to the original list order with the index on the current song, so `next()` is the song that follows it in the original list.
- Toggling relabels upcoming non-manual entries with `source.withShuffle(on)`.
- Repeat `OFF`: `next()` and `advanceOnTrackEnd()` return null at the end. `ALL`: both wrap to the first entry (re-shuffled first when `shuffled`). `ONE`: `advanceOnTrackEnd()` returns the current entry again; `next()` moves on and wraps at the end.
- `window(...)` shows wrapped entries after the end when repeat is `ALL`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// PlayQueueTest
@Test fun `play next goes right after current, add to queue goes after those`() { /* [A*, B, C]; addToQueue(X); playNext(Y); addToQueue(Z) → A, Y, X, Z, B, C */ }
@Test fun `manual entries always precede non-manual upcoming entries`() { }
@Test fun `replace keeps queued songs after the new current`() { /* → N*, Y, X, Z, rest of new list */ }
@Test fun `tapping a song plays the list in order`() { /* replace([A,B,C,D], startIndex = 1, ARTIST, shuffled = false) → current B, then C, D; previous() is A */ }
@Test fun `window at index 5 of 16000 with 20 and 20 has 26 entries`() { }
@Test fun `removeAt an upcoming index leaves current unchanged`() { }

// PlayQueueShuffleTest (strategy = reverse order, so results are exact)
@Test fun `shuffle on keeps the current song and reorders only what is left`() { /* [A, B*, C, D, E] → A, B*, E, D, C */ }
@Test fun `shuffle off restores the original order around the current song`() { /* from A, B, E*, D, C → A, B, C, D, E* with index on E; previous() is D */ }
@Test fun `manual entries do not move in either direction`() { /* [A*, Y(manual), B, C] → A*, Y, C, B → A*, Y, B, C */ }
@Test fun `toggle relabels artist and songs sources only`() { /* ARTIST → ARTIST_SHUFFLE and back; ALBUM stays ALBUM; manual stays QUEUE */ }
@Test fun `starting shuffled puts the tapped song first`() { /* replace(…, startIndex = 2, shuffled = true).current == songs[2] */ }
@Test fun `shuffled reflects the last replace or toggle`() { }

// PlayQueueRepeatTest
@Test fun `off stops at the end`() { /* next() == null, advanceOnTrackEnd() == null, index unchanged */ }
@Test fun `all wraps to the first song`() { }
@Test fun `all with shuffle reshuffles on wrap`() { }
@Test fun `one repeats the same song when it ends`() { /* advanceOnTrackEnd() === current, index unchanged */ }
@Test fun `one still moves on when the user presses next`() { }
@Test fun `window wraps under repeat all`() { /* at the last of 5 with ahead = 3 → last, first, second, third */ }
@Test fun `repeat mode survives replace`() { }
```

- [ ] **Step 2:** `./gradlew :playback:testDebugUnitTest` → FAIL. Implement. PASS. Write the ADR "queue modes: shuffle toggle and repeat" (A1, A2, C7). Commit: `feat(playback): play queue with play-next, shuffle toggle and repeat`.

### Task 14: Playback service, controller, walking skeleton

**Files:** Create `playback/{m}/playback/{PlaybackController,PlayRequest,PlaybackState,PlaybackService,MediaControllerPlaybackController,MediaItemMapper}.kt`, `…/queue/QueueWindowSync.kt`, `…/di/PlaybackModule.kt`; modify `playback/src/main/AndroidManifest.xml` (service with `foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, Media3 `MediaButtonReceiver`), `playback/build.gradle.kts`; create `app/src/debug/kotlin/dev/tomasflopes/musicplayer/debug/{DebugScreen,DebugViewModel}.kt` · Test `playback/{t}/playback/{MediaItemMapperTest,QueueWindowSyncTest}.kt`, `playback/{a}/playback/PlaybackServiceTest.kt`

**Interfaces (produces):**

```kotlin
data class PlayRequest(val songs: List<SongRef>, val startIndex: Int, val shuffle: Boolean, val source: PlaySource, val picked: Boolean)
data class PlaybackState(val current: Song?, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long,
    val upcoming: List<Song>, val hasPrevious: Boolean, val hasNext: Boolean, val shuffle: Boolean, val repeatMode: RepeatMode)
interface PlaybackController {
    val state: StateFlow<PlaybackState>
    fun play(request: PlayRequest); fun playNext(songs: List<SongRef>); fun addToQueue(songs: List<SongRef>)
    fun togglePlayPause(); fun next(); fun previous(); fun seekTo(positionMs: Long); fun jumpToUpcoming(index: Int)
    fun setShuffle(on: Boolean); fun cycleRepeatMode()          // OFF → ALL → ONE → OFF
}
```

Rules: one `MediaSessionService`, one ExoPlayer, audio attributes = media/music with audio focus handled, pause on becoming noisy. Shuffle is done by `PlayQueue` with the injected `ShuffleStrategy` (ExoPlayer's own shuffle stays off). Toggling shuffle rewrites only the player items after the current one, so the playing song is never interrupted. Repeat one uses the player's own repeat-one on the current item (gapless loop); repeat all is the queue window wrapping. The session exposes a forwarding player that routes shuffle and repeat commands from outside controllers (car, headset, notification) to `PlayQueue` and reports the queue's modes back (A6). `QueueWindowSync` keeps the player's playlist equal to `PlayQueue.window(...)` and tops it up on every transition. Media item metadata: title, artist, album and artwork from the tags. Session activity opens Now Playing. Notification = Media3 default provider (no custom notification code). A source that fails to open is removed from the queue and playback moves on.

- [ ] Tests (JVM): mapper puts tag title/artist/album into the item unchanged (symbols and accents included); window sync, driven by a fake player, never holds more than `windowBehind + windowAhead + 1` items, appends exactly the missing entries after `next()`, and after `setShuffled` replaces only items after the current index.
- [ ] Tests (emulator): play 3 sample songs → `isPlaying` within 5 s and metadata equals the tags; `next()` twice lands on the third; a 100-song request leaves ≤ 41 items in the player; ten `next()` calls within one second land on the eleventh song (Review Focus 4); a request containing a deleted file plays the following song; `setShuffle(true)` mid-song leaves the current item and position running and changes the next item; repeat one starts the same song again at its end; repeat all goes from the last song to the first; a shuffle command sent through a second `MediaController` flips `PlaybackState.shuffle`.
- [ ] Implement. Build `DebugScreen` (debug source set, reachable from a launcher shortcut for now): Pick folder · Scan (progress) · Shuffle all · Pause · Next · Shuffle toggle · Repeat · song count · last 50 `play_event` rows (empty until Task 16).
- [ ] **Ask, then** install on the emulator; shuffle the sample set; check the notification by hand. Commit: `feat(playback): Media3 service, windowed queue, debug screen`.

### Task 15: Checkpoint: notification, car Bluetooth, Android 17 audio rules (guidelines §4.3 #3)

Owner + real phone + the car. No new code unless a check fails. Result goes into `docs/adr/0008-background-audio-and-car-controls.md`.

Why this is early: Android 17 silences background audio from apps targeting API 37 unless a foreground service with while-in-use capability is running, and a service started from the background may not get that capability. Media3's service is the recommended path, but "press play in the car while the phone is in a pocket" is exactly the case to prove on the real device.

- [ ] Notification shows title, artist, album, cover; play/pause/next/previous work; still playing after 10 minutes with the screen off; lock-screen controls work.
- [ ] Car: track info on the display; steering-wheel next / previous / pause; display updates on track change; a phone call pauses and playback resumes after; switching the car off pauses.
- [ ] Paused for 15+ minutes with the app in the background → play from the car starts audio. If silent, capture `adb shell dumpsys audio | grep -A5 hardening`.
- [ ] Headset button: single press toggles, double press skips.
- [ ] If the car has shuffle or repeat buttons: they change the mode and Now Playing agrees; if the car shows the mode, it matches.
- [ ] A gapless album of yours plays without gaps.
- [ ] Shuffle all on the full library starts in under 500 ms (stopwatch on the debug screen).
- [ ] Any failure → fix in `:playback` with a regression test, re-run the list. If "play from the car after a long pause" cannot be made reliable under the Android 17 rules, stop and bring the options to the owner (keep the service alive while paused for N minutes, or accept opening the app first).

### Task 16: Listening log recorder

**Files:** Create `playback/{m}/playback/log/{PlayEventTracker,PlayEventRecorder,PendingPlayStore}.kt` · Test `playback/{t}/playback/log/{PlayEventTrackerTest,PendingPlayStoreTest}.kt`, `playback/{a}/playback/PlayEventRecorderTest.kt`

**Interfaces (produces):**

```kotlin
class PlayEventTracker(private val nowMs: () -> Long) {          // pure state machine; returns the finished event, if any
    fun onStarted(entry: QueueEntry, durationMs: Long, startedBy: StartedBy, shuffle: Boolean): PlayEvent?   // finishes the previous one as SKIPPED
    fun onPlayingChanged(isPlaying: Boolean)
    fun onEndedNaturally(): PlayEvent?                            // COMPLETED
    fun onStopped(): PlayEvent?                                   // STOPPED
    fun pending(): PlayEvent?                                     // what a crash right now would lose
}
class PendingPlayStore(dir: File) { fun save(e: PlayEvent?); fun takeLeftover(): PlayEvent? }
class PlayEventRecorder  // Player.Listener → tracker → PlayHistoryWriter.append; checkpoints pending() every pendingCheckpointMs
```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `natural end is completed with the full duration`() { at(1_000); started(song = 7, SHUFFLE_ALL, duration = 200_000, PICKED, shuffle = true); playing(true); at(201_000)
    assertEquals(PlayEvent(SongId(7), 1_000, 200_000, 200_000, COMPLETED, SHUFFLE_ALL, PICKED, shuffle = true), tracker.onEndedNaturally()) }
@Test fun `tapped song is picked, the one after it is auto, a next press is nav`() { }
@Test fun `shuffle flag is the queue's state when the song starts`() { /* toggle mid-song does not change the running event */ }
@Test fun `repeat one logs one completed event per loop, each started by auto`() { }
@Test fun `next after 5 seconds is skipped with 5000 ms`() { }
@Test fun `paused time is not listened time`() { /* play 10 s, pause 600 s, play 20 s, next → listenedMs = 30_000, SKIPPED */ }
@Test fun `seeking forward adds nothing`() { /* play 10 s, seek +120 s, play 5 s → 15_000 */ }
@Test fun `stopping is stopped`() { }
@Test fun `three quick skips are three events in order, each under 15000 ms`() { }
@Test fun `a song that never became audible is still logged with 0 ms`() { }
@Test fun `manual queue entry is logged with source queue`() { }
@Test fun `leftover checkpoint is appended once as stopped on next start`() { /* save, new store instance, takeLeftover() == event with STOPPED; second take is null */ }
```

- [ ] **Step 2:** Run, implement, pass. Emulator test: play, skip, let one finish → three rows in `play_event` with the right `ended_by`, `source`, `started_by` and `shuffle`; kill the process mid-song, restart → one `stopped` row for it.
- [ ] **Step 3:** Confirm rows appear on the debug screen. Commit: `feat(playback): record play_event from day one`.

### Task 17: Remember the queue; resume from car and headset

**Files:** Create `playback/{m}/playback/queue/QueueStateStore.kt`; modify `PlaybackService.kt` (`onPlaybackResumption`, restore on create) · Test `playback/{t}/playback/queue/QueueStateStoreTest.kt`, `playback/{a}/playback/PlaybackResumptionTest.kt`

**Interfaces (produces):** `class QueueStateStore(dir: File) { fun save(queue: QueueSnapshot, positionMs: Long); fun load(): SavedQueue? }`; `data class SavedQueue(val queue: QueueSnapshot, val positionMs: Long)`; `QueueSnapshot` (Task 13) carries the current order, the original order, the index, `shuffled` and `repeatMode`. Binary file with a format version; written on queue change (debounced 1 s) and on pause.

- [ ] Tests: 16 000 entries round-trip equal, with `shuffled = true`, both orders and `repeatMode = ONE` intact (so shuffle off after a restart still restores the original order); truncated or garbage file → `null`, not a crash; unknown format version → `null`; entries whose song has left the library are dropped on restore and the index follows the current song.
- [ ] Emulator: play, stop the service, send a media-button play (`adb shell input keyevent 126`) → the saved song plays from the saved position.
- [ ] **Owner, in the car:** phone connects with the app not running → play resumes the last queue. Add the result to ADR 0008. Commit: `feat(playback): queue persistence and playback resumption`.

---

## Milestone 4: search and featured artists (`:core:search`)

### Task 18: Text normalising (search ignores case, accents and symbols)

**Files:** Create `core/search/{m}/core/search/TextNormalizer.kt` · Test `core/search/{t}/core/search/TextNormalizerTest.kt`

**Interfaces (produces):**

```kotlin
object TextNormalizer {
    fun spaced(text: String): String          // lower-case, accents stripped (NFD), every run of non letter/digit → one space, trimmed
    fun compact(text: String): String         // lower-case, accents stripped, every character that is not a letter, digit or space removed, spaces collapsed, trimmed
    fun variants(text: String): Set<String>   // spaced, compact, and compact with '$' read as 's' (A5); blanks dropped, duplicates collapse
    fun tokens(normalized: String): List<String>
}
```

Used only for matching. Nothing normalised is ever stored as display text or shown.

- [ ] **Step 1: Write the failing tests**

```kotlin
@ParameterizedTest @CsvSource("JAY-Z,jay z", "A\$AP Rocky,a ap rocky", "m.A.A.d city,m a a d city", "Song (Remix),song remix", "DAMN.,damn", "'  Beyoncé ',beyonce", "Ed,ed")
fun spaced(text: String, expected: String) = assertEquals(expected, TextNormalizer.spaced(text))
@ParameterizedTest @CsvSource("JAY-Z,jayz", "A\$AP Rocky,aap rocky", "m.A.A.d city,maad city", "N.W.A,nwa", "Mr. Morale,mr morale", "Song (Remix),song remix")
fun compact(text: String, expected: String) = assertEquals(expected, TextNormalizer.compact(text))
@Test fun `variants of a plain name is one string`() = assertEquals(setOf("ed"), TextNormalizer.variants("Ed"))
@Test fun `variants of a hyphenated name`() = assertEquals(setOf("jay z", "jayz"), TextNormalizer.variants("JAY-Z"))
@Test fun `dollar sign also reads as s`() = assertEquals(setOf("a ap rocky", "aap rocky", "asap rocky"), TextNormalizer.variants("A\$AP Rocky"))
@Test fun `dollar before a number keeps the plain form too`() = assertEquals(setOf("100 bill", "s100 bill"), TextNormalizer.variants("\$100 Bill"))
@Test fun `only symbols gives nothing`() = assertEquals(emptySet<String>(), TextNormalizer.variants("(-.)"))
@Test fun `digits and non-latin letters are kept`() = assertEquals("21 привет", TextNormalizer.spaced("21 Привет!"))
@Test fun tokens() = assertEquals(listOf("kendrick", "lamar"), TextNormalizer.tokens("kendrick lamar"))
```

- [ ] **Step 2:** `./gradlew :core:search:test` → FAIL. Implement. PASS. Write the ADR "search matching ignores accents and symbols; display is as tagged" (A3, A5). Commit: `feat(search): text normalising that ignores accents and symbols`.

### Task 19: Featured-artist parser

**Files:** Create `core/search/{m}/core/search/FeaturedArtistParser.kt`; modify `data/{m}/data/scan/LibraryScanner.kt` (recompute `song_feature` after every scan that changed songs or artists), `data/build.gradle.kts` (`:core:search`) · Test `core/search/{t}/core/search/FeaturedArtistParserTest.kt`, extend `LibraryScannerTest`

**Interfaces (produces):**

```kotlin
data class KnownArtist(val id: ArtistId, val name: String)
sealed interface FeaturedCredit { data class Known(val artistId: ArtistId, val name: String) : FeaturedCredit; data class Raw(val text: String) : FeaturedCredit }
class FeaturedArtistParser(knownArtists: Collection<KnownArtist>) { fun parse(title: String, mainArtistId: ArtistId? = null): List<FeaturedCredit> }
```

Rules (design §7.11): marker = `feat.`, `feat`, `ft.`, `ft`, case-insensitive, whole word, optionally inside `(` or `[`. Feature text runs to the closing bracket, the next opening bracket, or the end. `,` and `&` are separators. Names are matched against known artists on `TextNormalizer.spaced` text of both sides (so symbols and accents never block a match), longest first, left to right; whatever is left between matches is one `Raw`. The main artist is never returned.

- [ ] **Step 1: Write the failing tests** (known = Ed, Kendrick, Kendrick Lamar, SZA, Travis Scott, 21 Savage, A$AP Rocky, JAY-Z, Beyoncé)

```kotlin
@Test fun `the design's example`() = assertKnown("Song feat. Ed Kendrick Lamar", "Ed", "Kendrick Lamar")
@ParameterizedTest @ValueSource(strings = ["Song ft. SZA", "Song feat SZA", "Song FEAT. sza", "Song Ft SZA", "Song (feat. SZA)", "Song [ft. SZA]"])
fun `every marker form`(title: String) = assertKnown(title, "SZA")
@Test fun `stops at the next bracket`() = assertKnown("Song (feat. SZA) [Remix]", "SZA")
@Test fun `also without brackets`() = assertKnown("Song feat. SZA Travis Scott (Remix)", "SZA", "Travis Scott")
@Test fun `commas and ampersands`() = assertKnown("Song feat. SZA, Travis Scott & 21 Savage", "SZA", "Travis Scott", "21 Savage")
@Test fun `longest name wins`() = assertKnown("Song feat. Kendrick Lamar", "Kendrick Lamar")
@Test fun `shorter name when that is all there is`() = assertKnown("Song feat. Kendrick", "Kendrick")
@Test fun `symbols and accents`() = assertKnown("Song feat. A\$AP Rocky JAY-Z beyonce", "A\$AP Rocky", "JAY-Z", "Beyoncé")
@Test fun `unknown names are kept raw for search`() = assertEquals(listOf(Known(sza), Raw("Someone New")), parse("Song feat. SZA Someone New"))
@ParameterizedTest @ValueSource(strings = ["Defeat", "Left Behind", "Drift", "Aftermath", "Song", "Song feat.", "Lift (Live)"])
fun `no marker, no features`(title: String) = assertEquals(emptyList<FeaturedCredit>(), parse(title))
@Test fun `artist prefix in the title is ignored`() = assertKnown("Ed - Song feat. SZA", "SZA")
@Test fun `main artist is not a feature of their own song`() = assertEquals(emptyList<FeaturedCredit>(), parse("Song feat. SZA", mainArtistId = sza))
```

- [ ] **Step 2:** Run, implement, pass. Scanner test: after scanning the sample set, `observeFeaturedOn(Ed)` returns the three sample songs that feature Ed and none of Ed's own. Commit: `feat(search): featured artists from titles`.

### Task 20: Match tiers, `SearchMatcher`, search index (the "Ed" case)

**Files:** Create `core/search/{m}/core/search/{MatchTier,SearchField,SearchDoc,SearchMatcher,FieldMatcher,SearchTieBreaker,SearchConfig,SearchResults,SearchIndex}.kt`; `core/search/{t}/core/search/FakeLibrary.kt` (16 000 songs, 1 500 artists, 2 000 albums, seeded) · Test `core/search/{t}/core/search/{FieldMatcherTest,SearchMatcherContract,SearchIndexTest,SearchPerformanceTest}.kt`

**Interfaces (produces):**

```kotlin
enum class MatchTier { EXACT, WORD, SUBSTRING }                       // best first
enum class SearchField { TITLE, ARTIST, ALBUM_ARTIST, ALBUM, FOLDER, FEATURED }
data class SearchDoc(val type: SearchItemType, val id: Long, val display: String, val fields: Map<SearchField, List<String>>)  // display as tagged; field values = every TextNormalizer variant
interface SearchMatcher { val field: SearchField; fun match(query: String, doc: SearchDoc): MatchTier? }
class FieldMatcher(override val field: SearchField) : SearchMatcher
fun interface SearchTieBreaker { fun compare(a: SearchDoc, b: SearchDoc): Int }          // phase 1: alphabetical; phase 2: recent score
data class SearchConfig(val sectionSize: Int = 4)
data class SearchHit(val doc: SearchDoc, val tier: MatchTier, val field: SearchField)
data class SearchResults(val top: SearchHit?, val artists: List<SearchHit>, val albums: List<SearchHit>, val songs: List<SearchHit>,
    val moreArtists: Boolean, val moreAlbums: Boolean, val moreSongs: Boolean)
interface SearchEngine {                                                               // decision point: injected, so another engine can be benchmarked beside this one
    fun rebuild(artists: List<Artist>, albums: List<Album>, songs: List<Song>, features: List<SongFeature>)
    fun search(query: String): SearchResults                                            // EXACT and WORD only
    fun searchAll(query: String, type: SearchItemType): List<SearchHit>                 // "See all": SUBSTRING included, last
}
class SearchIndex(matchers: Set<SearchMatcher>, tieBreaker: SearchTieBreaker, config: SearchConfig) : SearchEngine   // in-memory
```

The query is expanded with `TextNormalizer.variants` too; a field matches at the best tier found between any query variant and any value variant. Tier rules for a query variant `q` against a value variant `v`: EXACT `v == q` · WORD `v` starts with `q`, or `q` starts at a word boundary of `v` · SUBSTRING `v` contains `q` anywhere else. A doc's tier is its best field tier. Order: tier, then field (TITLE, FEATURED, ARTIST, ALBUM_ARTIST, ALBUM, FOLDER), then tie-breaker. Top result = best tier, then type ARTIST > ALBUM > SONG.

- [ ] **Step 1: Write the failing tests** (library: artists Ed, Eddie Kane, Kendrick Lamar, A$AP Rocky, JAY-Z, N.W.A, Beyoncé; songs `Faded`, `Ended`, `m.A.A.d city` and `Song (Remix)` by Kendrick Lamar, `Morning` by Ed, `Night feat. Ed` by Kendrick Lamar, `Big Ed` by Eddie Kane; albums `Education`, `DAMN.` and `Mr. Morale` by Kendrick Lamar)

```kotlin
@Test fun `Ed puts the artist Ed first`() { val r = index.search("Ed"); assertEquals(ARTIST, r.top!!.doc.type); assertEquals("Ed", r.top!!.doc.display) }
@Test fun `exact before starts-with`() = assertEquals(listOf("Ed", "Eddie Kane"), index.search("ed").artists.map { it.doc.display })
@Test fun `mid-word matches are not in the main results`() = assertTrue(index.search("ed").songs.none { it.doc.display in setOf("Faded", "Ended") })
@Test fun `mid-word matches are last under See all`() { val all = index.searchAll("ed", SONG).map { it.doc.display }
    assertTrue(all.containsAll(listOf("Faded", "Ended"))); assertTrue(all.indexOf("Big Ed") < all.indexOf("Faded")) }
@Test fun `featured and own songs are found by artist name`() { val songs = index.searchAll("ed", SONG).map { it.doc.display }
    assertTrue(songs.containsAll(listOf("Morning", "Night feat. Ed"))) }
@Test fun `case, spaces and accents do not matter`() = assertEquals(index.search("ed"), index.search("  ÉD "))
@Test fun `multi-word prefix`() = assertEquals("Kendrick Lamar", index.search("kendrick l").top!!.doc.display)
@ParameterizedTest @ValueSource(strings = ["jayz", "jay z", "jay-z", "JAY Z", "Jay.Z"])
fun `JAY-Z is found with or without its symbols`(query: String) = assertEquals("JAY-Z", index.search(query).top!!.doc.display)
@ParameterizedTest @CsvSource("asap rocky,A\$AP Rocky", "a\$ap,A\$AP Rocky", "maad city,m.A.A.d city", "m.a.a.d,m.A.A.d city", "nwa,N.W.A", "n.w.a,N.W.A",
    "damn,DAMN.", "song remix,Song (Remix)", "(remix),Song (Remix)", "beyonce,Beyoncé", "mr morale,Mr. Morale")
fun `accents and symbols are ignored when matching, and the hit is shown as tagged`(query: String, display: String) =
    assertEquals(display, index.search(query).top!!.doc.display)
@Test fun `sections hold at most 4 and say when there is more`() { }
@Test fun `blank query is empty`() = assertNull(index.search("   ").top)
@Test fun `a matcher added to the set is used without touching SearchIndex`() { /* custom matcher on FOLDER returning EXACT for "zzz" */ }
@Tag("perf") @Test fun `16k songs search under 100 ms, build under 1 s`() { /* median of 20 runs of search("ed") */ }
```

- [ ] **Step 2:** Run, implement, pass. Commit: `feat(search): tiered search index with pluggable matchers`.

---

## Milestone 5: the app (polished from the start)

### Task 21: Design review (owner checkpoint), then the design system

**Files:** Create `app/{m}/ui/theme/{Color,Type,Shape,Spacing}.kt` (modify `Theme.kt`), `app/{m}/ui/components/{SongRow,AlbumCard,ArtistRow,FolderRow,SectionHeader,AlbumHeader,CoverArt,PlayShuffleButtons,SongMenu,EmptyState,MiniPlayer,FastScroller}.kt`, vector icons under `app/src/main/res/drawable/`; modify `config/detekt/detekt.yml` (Compose naming and theme-constant exceptions), `AndroidApplicationConventionPlugin.kt` (foundation, animation) · Test `app/{t}/ui/components/FastScrollerStateTest.kt`

**Interfaces (produces):** `MusicPlayerTheme`; `Spacing` tokens; stateless composables taking data + callbacks, each with a `@Preview` on the dark background: `SongRow(song, onClick, onMenu, showTrackNumber = false)`, `AlbumCard(album, onClick)`, `ArtistRow(artist, onClick)`, `FolderRow(folder, onClick)`, `AlbumHeader(album, onPlay, onShuffle)`, `MiniPlayer(state, onToggle, onNext, onOpen)`, `FastScroller(sections: List<Section>, onJump: (firstIndex: Int) -> Unit)`, `SongMenu(onPlayNext, onAddToQueue)`.

- [ ] **Step 1 (owner checkpoint, before any screen code):** I mock up Home, Library (Songs with the A–Z rail), Album page, Now Playing with the mini-player, and Search in the dark + orange direction of design §8.1, and you pick or correct. Colours, type scale, spacing and corner radii are fixed from that review.
- [ ] **Step 2:** Test `FastScroller`'s pure part: a touch at fraction 0.5 of 27 sections selects section 13; it reports the section's `firstIndex`. Implement tokens and components with previews.
- [ ] **Step 3:** `./gradlew check` (detekt passes with the Compose exceptions). Commit: `feat(ui): design system`.

### Task 22: App shell, navigation, multibindings, embedded cover art

**Files:** Create `app/{m}/navigation/{Routes,AppNavHost,BottomBar}.kt`, `app/{m}/di/{AppModule,ExtensionPointsModule}.kt`, `app/{m}/home/HomeSection.kt`, `app/{m}/ui/art/EmbeddedCoverFetcher.kt`; modify `MainActivity.kt`, `MusicPlayerApp.kt` (WorkManager + Hilt, Coil image loader), `app/build.gradle.kts`, the catalog · Test `app/{t}/di/ExtensionPointsTest.kt`, `app/{t}/ui/art/CoverKeyTest.kt`

**Interfaces (produces):**

```kotlin
@Serializable sealed interface Route { Home, Library, Search, NowPlaying, Settings, Onboarding, DynamicList(id: String), AlbumDetail(id: Long), ArtistDetail(id: Long), FolderDetail(path: String) }
interface HomeSection { val id: String; val order: Int; @Composable fun Content(onNavigate: (Route) -> Unit) }
// Hilt sets: Set<DynamicListProvider>, Set<SearchMatcher>, Set<HomeSection>; single ShuffleStrategy; Clock; LibraryConfig, SearchConfig, PlaybackConfig; @IoDispatcher, @DefaultDispatcher
```

Cover art: Coil fetcher keyed by album id (or song id for songs without an album), bytes from `CoverReader` through `FileTagSource`, decoded at the requested size, memory + disk cache, no network component.

- [ ] Tests: `ExtensionPointsTest` builds the sets the module declares and asserts `recently_added` is in the provider set, six `FieldMatcher`s (one per `SearchField`) are in the matcher set, and section ids are unique; cover cache key is the same for two songs of one album.
- [ ] Implement: bottom bar (Home · Library · Search), mini-player slot above it, start destination = Onboarding when no music root, else Home. Remove the launcher shortcut to `DebugScreen`; reach it from Settings in debug builds.
- [ ] Commit: `feat(app): shell, navigation, extension-point registries, cover art`.

### Tasks 23 to 30: screens

Each task follows the same shape: `XUiState.kt`, `XViewModel.kt` (one `StateFlow<XUiState>`, events in), `XScreen.kt` (stateless, previewable), a ViewModel unit test with fakes of the core ports (`app/{t}/<feature>/XViewModelTest.kt`, JUnit + Turbine), install on the emulator, commit. The fakes live in `app/{t}/fakes/` (`FakeSongReader`, `FakePlaybackController`, `FakeSettingsRepository`, …) and are created in Task 23.

| Task | Feature (package under `app/{m}/`) | Behaviour to build | ViewModel tests (assertions) |
|---|---|---|---|
| 23 | `onboarding/` | Welcome → `READ_MEDIA_AUDIO` → folder picker → first scan with progress → Home. Permission denied: explain and offer Settings. Unsupported folder (not internal storage or SD card): say so, pick again. Zero MP3s found: say so, offer to pick again. | state walks `Welcome → NeedsFolder → Scanning(done, total) → Done`; denied permission → `PermissionDenied`; unmapped tree id → `UnsupportedFolder`; `total = 0` → `EmptyFolder`; root saved exactly once |
| 24 | `home/`, `home/sections/{ShuffleAllSection,DynamicListsSection}`, `home/dynamiclist/` | Home renders the `HomeSection` set in `order`. Shuffle all → `PlayRequest(allSongRefs(), 0, shuffle = true, SHUFFLE_ALL, picked = false)`. One card per `DynamicListProvider`. Recently added screen: caption "Last 20 days" (the chip with options 7 / 15 / 20 / 30 / 60 / 90 + custom was dropped, ADR 0022); the value applies at once; Play and Shuffle with source `RECENTLY_ADDED`. | shuffle-all sends every song exactly once with `shuffle = true` and `SHUFFLE_ALL`; choosing 15 calls `setRecentlyAddedDays(15)` and the list re-emits; custom `0` and `5000` are rejected; a second fake provider appears on Home with no Home code change |
| 25 | `library/` (`songs/`, `artists/`, `albums/`) | Tabs Artists · Albums · Folders · Songs; lazy lists with stable keys; A–Z rail from `SectionIndexer`; song tap plays from that song in list order (source `SONGS`, shuffle off, `picked = true`); song menu → Play next / Add to queue. | sections match sort keys; tapping the 3rd song sends `startIndex = 2`, `shuffle = false`, `picked = true`, `SONGS`; menu actions call `playNext` / `addToQueue` with that id |
| 26 | `library/album/`, `library/artist/` | Album: cover, album artist, tracks in disc → track order with numbers shown, disc headers when more than one disc, Play / Shuffle (source `ALBUM`; Play is never shuffled). Artist: albums, own songs, "Featured on"; tapping a song plays the artist's own songs in the listed order from that song (source `ARTIST`, shuffle off); the Shuffle button starts them shuffled (source `ARTIST_SHUFFLE`). | album Play sends ids in `AlbumOrder`; disc headers only when discs > 1; tapping an artist's 2nd song sends the listed order with `startIndex = 1`, `ARTIST`, `shuffle = false`; artist shuffle sends `shuffle = true`, `ARTIST_SHUFFLE`, own songs only; Featured on lists `observeFeaturedOn`, after own songs |
| 27 | `library/folders/` | Tree from the music root; folders first, then songs in natural file-name order; Play / Shuffle a folder (direct songs, source `FOLDER`); system back goes up one level. | root shows `observeChildren(null)`; entering and backing restores the parent; play sends the folder's songs in display order |
| 28 | `search/` | Field with results while typing (index rebuilt off the main thread when the library changes); Top result card with Play / Shuffle; Artists · Albums · Songs with "See all"; choosing any result appends a `SearchEvent`; playing a song from search uses source `SEARCH`. | typing "ed" yields top = artist Ed; typing "jayz" yields top = `JAY-Z`, displayed as tagged; choosing it appends `SearchEvent("ed", ARTIST, id)`; choosing a song appends the event and sends a `SEARCH` request; See all shows mid-word matches last; blank query shows the empty state |
| 29 | `nowplaying/` | Sheet: large cover, title / artist / album, seek bar, previous / play-pause / next, **shuffle toggle** (shuffles or restores the rest of the queue, current song keeps playing), **repeat button** (off → all → one), Up next list (tap to jump, manual entries marked; reorders live when shuffle is toggled). Mini-player above the tabs on every screen, hidden when nothing is loaded. | state mirrors `PlaybackController.state`; seek calls `seekTo`; shuffle button calls `setShuffle(!state.shuffle)`; repeat button calls `cycleRepeatMode()`; both icons reflect `state.shuffle` / `state.repeatMode`; tapping upcoming item 2 calls `jumpToUpcoming(2)`; mini-player hidden when `current == null` |
| 30 | `settings/`, `debug/` | Music folder (shows the path; change → picker → rescan; songs outside leave the lists, history stays); Recently added window (same value as the chip); debug builds: link to `DebugScreen`. | changing the folder saves the new root and requests a scan; days edit is the same setting the chip reads |

Per task: - [ ] write the ViewModel test (fails) · - [ ] implement state, ViewModel, screen with previews · - [ ] test passes, `./gradlew check` · - [ ] install on the emulator, walk the flow with the sample set · - [ ] commit `feat(<feature>): …`.

---

## Milestone 6: hardening and switching over

### Task 31: UI flow tests, performance on the phone, documentation

**Files:** Create `app/{a}/{OnboardingFlowTest,ShuffleAllFlowTest}.kt`, `docs/adr/0009-windowed-queue.md`, `docs/adr/0010-song-identity-and-retired-songs.md`; modify `README.md`, `CONTRIBUTING.md`

- [ ] `OnboardingFlowTest`: fresh install, permission granted, sample root pre-set through a test hook → scan finishes → Home shows Shuffle all and Recently added. `ShuffleAllFlowTest`: tap Shuffle all → mini-player shows a title from the sample set and the pause icon within 5 s.
- [ ] **Owner + phone:** measure against the budget (cold start < 1.5 s, search < 100 ms per keystroke, Shuffle all < 500 ms, no dropped frames fast-scrolling Songs). If Songs or Artists stutter, switch those two lists to Paging 3 (guidelines §3.3; one more dependency, asked first).
- [x] `CONTRIBUTING.md` recipes, each naming the interface, the one binding line, and the contract test to extend: add a dynamic list · add a search field or tier · add a Home section · swap the shuffle strategy · change the schema. `README.md`: status, sample set commands, `deviceCheck`.
- [x] `docs/adr/INDEX.md` lists every ADR written during phase 1, one line each.
- [ ] `./gradlew check deviceCheck` green. Commit.

Not recorded as done: the two UI flow tests have not been run on the emulator, the phone measurements and the Paging 3 decision are the owner's, and `deviceCheck` has not been run.

### Task 32: Acceptance: can Vinyl go?

Owner runs both apps side by side for a few days, then ticks:

- [x] Onboarding picked the music folder; no WhatsApp audio, recordings or ringtones in any list.
- [x] Song, artist, album and album-artist text match your tags exactly on five songs you choose.
- [x] Five albums you play often list and play in the tagged order, across discs.
- [x] Folders, Artists, Albums, Songs all there; A–Z rail jumps correctly.
- [x] Recently added shows the same songs as Vinyl at 20 days; changing it to 15 applies at once (edited in Settings only, ADR 0022).
- [x] Searching "Ed" shows the artist Ed first; "Faded" / "Ended" only under See all.
- [x] An artist page shows "Featured on" with songs where they are credited in the title.
- [x] Shuffle all, artist shuffle, Play next, Add to queue behave as expected.
- [x] Tapping a song on an artist page plays the songs in order; the shuffle button then shuffles the rest without interrupting the song; pressing it again restores the order.
- [x] Repeat all and repeat one work, also after the screen has been off.
- [x] Search finds names with symbols whether or not you type them (`jayz` / `jay-z`, `asap`, `maad city`), and shows them exactly as tagged.
- [x] Notification and lock-screen controls; car display and steering-wheel controls; resume from the car.
- [x] The debug screen shows a growing listening log with sensible `source` and `ended_by` values.
- [x] Nothing you used daily in Vinyl is missing. → Uninstall Vinyl. Phase 1 done; merge, tag `phase-1`.

---

## Sources checked while planning

- Android 17 background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio
- Media3 background playback and playback resumption: https://developer.android.com/media/media3/session/background-playback
