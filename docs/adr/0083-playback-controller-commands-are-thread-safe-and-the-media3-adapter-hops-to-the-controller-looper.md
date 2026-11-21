# 0083: PlaybackController commands are thread-safe; the Media3 adapter hops to the controller looper

Date: 2026-11-21 · Status: proposed

**Context.** With "Extend single songs" on, tapping a Search song crashed the app. `QueueRefiller` runs on a Default scope and called `appendBatch`; `MediaControllerPlaybackController` forwarded it to `MediaController.sendCustomCommand` on the caller's thread, and Media3 throws `IllegalStateException` for a call off its application (main) thread. The refiller scope had no handler, so the process died. The `controller` field and the `waiting` list had the same race before the controller connected.

**Decision.** The constraint is Media3's, so the Media3 adapter owns it. Every `PlaybackController` command may be called from any thread: `withController` runs the action inline on the main looper or posts it there, and `controller` and `waiting` are only touched on that looper (the controller is built with the main looper as its application looper and connects on it). Members that read the player and return a value (`removeFromQueue`, `position`, `queueContent`) stay main-thread only, documented on the interface. Callers do not hop. Defence in depth: the refiller catches a failed refill and reports it (`Log.w` with seed and epoch) and carries on, and its scope has a `CoroutineExceptionHandler`.

**Consequences.** Any worker may send commands. Commands from other threads arrive one main-loop turn later. A refill that throws is logged and dropped; the next signal tries again. Extends 0078, 0080.
