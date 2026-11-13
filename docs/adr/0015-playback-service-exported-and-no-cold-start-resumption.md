# 0015: Playback service stays exported; no cold-start resumption in phase 1

Date: 2026-11-13 · Status: accepted

**Context.** Lint warned `ExportedService` on `PlaybackService`, and Media3 logged `UnsupportedOperationException` from `MediaSession.Callback.onPlaybackResumption`. That log only appears when a `MediaButtonReceiver` is declared: it is Media3's hook to restart playback from a headset button when the app is not running.

**Decision.** The service stays `exported="true"` (the system UI, headsets and cars bind through its `MediaSessionService` intent filter) and the warning is suppressed on that one element with `tools:ignore`. The `MediaButtonReceiver` is removed: resuming a dead session needs the queue persisted (`PlayQueue.snapshot()` exists but is never saved), which is outside phase 1. A live session still gets media buttons.

**Consequences.** A headset button does nothing after the app process is gone. When a persisted queue is added, declare the receiver again and override `onPlaybackResumption` to restore it.
