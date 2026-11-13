# 0014: MediaStoreObserver starts from the Application

Date: 2026-11-13 · Status: accepted

**Context.** `MediaStoreObserver` was built and provided by Hilt, but nothing called `start()`, so the library was only rescanned by hand. The phase plan already rules that it starts from the Application.

**Decision.** `MusicPlayerApp.onCreate` calls `start()` once, after Hilt injection. It asks for a scan on app start (when a music root is set), on a debounced MediaStore change and when the music root changes. The scan itself still goes through `LibraryScanController`, so the observer holds no scan logic.

**Consequences.** The observer lives as long as the process, with its own `SupervisorJob` scope. A rescan on every process start is cheap because the scanner only reads files that changed. Not covered by a test; check on the phone by copying an MP3 into the music folder.
