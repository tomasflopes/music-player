# 0052: Rescan lives in Settings; Home keeps only the scan progress, and card captions clear the rounded corner

Date: 2026-11-14 · Status: accepted

**Context.** Home ended with a row of scan progress text and a "Rescan" button, which the owner never needs there: scans already run on app start, on MediaStore changes and on a new music folder (ADR 0014). Settings had no rescan entry. On the Home cards (Top 100, Recently added, playlists) the last line, "N songs", looked cut at its bottom-left. There is no border in the code: every card is one `ListCard`, whose whole column is clipped to `shapes.medium` (12 dp corners) for the rounded press area, and the caption sat flush against that corner.

**Decision.**
- *Rescan:* a "Rescan library" row in Settings' Library group (built-in row, not a `SettingsEntry`: the entries set is for contributions from other source sets, ADR 0019). It calls `LibraryScanController.requestScan()` through `SettingsViewModel.onRescan()`; while a scan runs the row is dimmed and ignores taps and its second line shows the progress. The words come from the same `ScanProgress.label()` and `isRunning()` Home used.
- *Home:* the Rescan button and `HomeViewModel.rescan()` are gone. Home keeps the scan progress line ("Looking for files…", "Reading tags 3/10", "Saving 4/10", "Scan failed") and now draws it only while there is something to say, so an idle Home has no empty row at the bottom. Home had no other empty-library handling (an empty library shows empty cards or none; Settings shows "Not set" for the folder), so nothing else moves.
- *Cards:* one fix in the one component. The caption is a shared `CardCaption` that ends `Spacing.xs` (12 dp, the corner radius) above the bottom edge, where the rounded corner no longer reaches, so the press area keeps its rounded ripple and the text is never cut. The subtitle may use two lines, so "N songs" wraps instead of being cut at a large font size. `NewPlaylistCard` uses the same caption. The cover, the card width and the colours are unchanged.
- *Rule calls (owner can overrule):* the row's idle text is "Look for new, changed and removed songs"; a failed scan stays visible on Home and in the row until the next scan; the cards grow 12 dp taller.

**Consequences.** No schema, port or Hilt-binding change. The look of the cards and the rescan row can only be judged on the phone, including at the largest font size.
