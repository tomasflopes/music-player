# Plan: Home screen cleanup (phase 1 components, UI only)

Decision: ADR 0052 (precedent: ADR 0048). No new behaviour, port or schema.

1. Home: `ScanStatus` shows text only; `HomeScreen` draws it only while `ScanProgress.label()` is not null; `HomeViewModel.rescan()` removed.
2. Settings: "Rescan library" row (`SettingRow` gets `enabled`), `SettingsViewModel.scan` and `onRescan()` over the existing `LibraryScanController`, `rescanSummary` for the row text.
3. Cards: `ListCard` and `NewPlaylistCard` share `CardCaption` (12 dp under the text, two-line subtitle). The Top 100, Recently added and playlist cards are all `ListCard`, so it is fixed once.
4. Tests: `HomeViewModelTest` (progress shown, no scan started), `SettingsViewModelTest` (rescan once, progress exposed), `ScanStatusTest`, `RescanSummaryTest`; `FakeScanController` now exposes its state.
5. `./gradlew check`, one commit.

Phone checks to do:
- Home: no Rescan button; with nothing scanning there is no empty row under the playlists.
- Settings, Library: "Rescan library" starts a scan; the row dims and shows "Reading tags n/N" then "Saving n/N"; it is tappable again when the scan ends; Home shows the same progress line while it runs.
- Pick a new music folder: Home shows the progress, Settings shows the same.
- Top 100 and Recently added: "N songs" is fully visible, with no cut at the bottom-left corner; the same on the playlist cards and "New playlist".
- Repeat with Android's font size at the largest setting: "N songs" is not truncated (it may wrap to two lines) and the row still scrolls cleanly.
