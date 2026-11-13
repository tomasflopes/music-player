# Music Player

A personal, fully offline Android music player for a large MP3 library. It replaces Vinyl for daily use and
adds taste-aware mixes, Wrapped-style stats and better search over time.

- Product design and decisions: the **Music Player** project on claude.ai (`design/2026-11-13-music-player-design.md`).
- How the code is built: the **Engineering Guidelines** doc in the same project; `CONTRIBUTING.md` summarises it.
- Why things are the way they are: [`docs/adr/`](docs/adr).

Status: **phase 1 (an MP3 player that replaces Vinyl) is mostly built**: Home with Shuffle all and Recently added,
Library (Artists, Albums, Songs), album and artist pages, search, Now Playing with the queue, notification and car
controls, Settings, and the listening log in the background. Onboarding is still the one-screen folder prompt.
Not done: measuring speed on a real phone against the budget in `docs/plans/2026-11-13-phase-1-mp3-player.md`
(Task 31), and the acceptance check (can Vinyl be uninstalled?). Nothing outside phase 1 exists yet (no mixes, playlists, analysis or Wrapped).

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
