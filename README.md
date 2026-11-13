# Music Player

A personal, fully offline Android music player for a large MP3 library. It replaces Vinyl for daily use and
adds taste-aware mixes, Wrapped-style stats and better search over time.

- Product design and decisions: the **Music Player** project on claude.ai (`design/2026-11-13-music-player-design.md`).
- How the code is built: the **Engineering Guidelines** doc in the same project; `CONTRIBUTING.md` summarises it.
- Why things are the way they are: [`docs/adr/`](docs/adr).

Status: **scaffold only.** Phase 1 (an MP3 player that replaces Vinyl) has not started.

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
```

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
