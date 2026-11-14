# Music Player

A personal, fully offline Android music player for a large MP3 library. It replaces Vinyl for daily use and
adds taste-aware mixes, Wrapped-style stats and better search over time.

- Product design and decisions: the **Music Player** project on claude.ai (`design/2026-11-13-music-player-design.md`).
- How the code is built: the **Engineering Guidelines** doc in the same project; `CONTRIBUTING.md` summarises it.
- Why things are the way they are: [`docs/adr/`](docs/adr).

Status: **phase 1 (an MP3 player that replaces Vinyl) is done** and tagged `phase-1`. It has Home with Shuffle all and
Recently added, Library (Folders, Artists, Albums, Songs with an A-Z rail), album and artist pages (with "Featured on"),
search, Now Playing with the queue, notification and car controls, Settings (music folder and the Recently added
window), and the listening log in the background. The owner ran the acceptance list of Task 32 on a phone next to
Vinyl and it passed.

**Phase 2, first slice (branch `phase2/top-100`, not merged):** a **Top 100** card on Home that follows current taste.
Every track the player moves to now gets its own listening-log row (next, previous, auto-advance, queue jump), and
`:core:taste` scores songs from the log: signed weights, softening of browsing bursts, completed plays forgiving
earlier skips, and a 30-day half-life, all in one `TasteConfig`. Scores are computed on demand behind a `ScoreBoard`
port (ADR 0023). Not built yet: smart shuffle, playlists, sound analysis, mixes.

**Post-test polish (branch `fix/post-test-polish`, not merged):** Search opens with the keyboard up, round A-Z bubble next to the rail, a tappable top-result card, songs show the file-name artist when the tag differs (ADR 0026), and artist shuffle includes "Featured on" songs (ADR 0027).

Not recorded: the speed measurements against the budget and a `deviceCheck` run (Task 31 in
`docs/plans/2026-11-13-phase-1-mp3-player.md`), and any run of the phase 2 slice (see section 4 of
`docs/plans/2026-11-14-phase-2-top-100.md`). Known gap, found by reading the code and not yet confirmed on the
phone: after the app process is gone, headset and car buttons do not resume playback because the queue is not
persisted (ADR 0015). Log rows written by phase 1 builds have unreliable listened times (ADR 0025).

## Setup (macOS)

1. Install JDK 21 (`brew install openjdk@21`) and Android Studio (Rabbit 2 / 2026.2 or newer; `compileSdk` 37 needs it).
2. First Studio launch: let the wizard install the Android SDK. Then in **SDK Manager** add **Android 17 (API 37)**,
   the **arm64** Android 17 system image, **Platform-Tools** and **Emulator**.
3. Clone, then run `scripts/setup.sh`. It enables the pre-push hook and lists anything still missing.
4. In Studio: **Device Manager > Create Device**, pick the Android 17 image, then open this folder and press Run.

Real phone (Android 17, USB debugging) is only needed for car Bluetooth, the full 16k library and on-device
analysis speed.

## Everyday commands

```sh
./gradlew check                 # ktlint + detekt + unit tests, all modules
./gradlew :core:model:check     # one module
./gradlew ktlintFormat          # fix formatting
./gradlew :app:installDebug     # install on the running emulator / phone
ANDROID_SERIAL=emulator-5554 ./gradlew deviceCheck   # Room, MediaStore and playback tests on the emulator
```

`deviceCheck` runs every module's instrumented tests and is **not** part of `check` or the pre-push hook
(ADR 0009). It runs on every connected device unless `ANDROID_SERIAL` picks one, so set it when the phone
is plugged in too. Besides the Room, scanner and playback tests it runs two UI flows in `:app`
(`OnboardingFlowTest`, `ShuffleAllFlowTest`). They need the sample library below on the device, they save the
sample folder as the music folder while they run (the previous folder is put back and rescanned afterwards), and
a scan of the sample folder retires every song outside it until the next scan. **Run them on the emulator only.**

## Sample library

A generated set of silent MP3s with real tags: albums with discs and tracks, albums without track numbers, search
traps, every ID3 version, covers, symbols, and a file outside the music folder on purpose.

```sh
./gradlew :core:tags:generateSamples   # writes core/tags/build/samples/ (Music/ and Recordings/)
scripts/push-samples.sh                # generates, pushes to /sdcard/ of the connected device, triggers a media scan
```

`push-samples.sh` writes to the device `adb` selects; set `ANDROID_SERIAL` when more than one is connected. Then pick `/sdcard/Music/Sample` as the music folder in the app.

There is no cloud CI. `./gradlew check` runs before every push through the `.githooks/pre-push` hook.

## Layout

| Module | Kind | Holds |
| --- | --- | --- |
| `:app` | Android app | Activity, navigation, Hilt wiring, Compose screens, ViewModels |
| `:core:model` | pure Kotlin | domain types |
| `:core:taste` | pure Kotlin | signal weights, decay, Top 100, smart shuffle, mix engine |
| `:core:search` | pure Kotlin | normalising, match tiers, ranking, featured-artist parsing |
| `:core:analysis` | pure Kotlin | sound-profile DSP (shared by the phone and the Mac analyser) |
| `:data` | Android library | Room, MediaStore scanner, DataStore, repositories |
| `:playback` | Android library | `MediaSessionService`, ExoPlayer, queue |
| `build-logic/` | Gradle | convention plugins: one place for SDK levels, JDK, ktlint and detekt |

`:core:*` never depends on Android.
