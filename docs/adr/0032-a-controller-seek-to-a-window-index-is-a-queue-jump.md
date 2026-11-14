# 0032: A controller seek to a window index is a queue jump, logged as picked

Date: 2026-11-14 · Status: accepted

**Context.** ADR 0025 logs every move the engine makes, but a controller (notification, car, headset, the app's own `MediaController`) can also call `seekTo(mediaItemIndex, position)` or `seekToDefaultPosition(mediaItemIndex)` on the session player. `QueueAwarePlayer` forwarded both straight to ExoPlayer, which changed song without the queue or the log knowing: the new song's listening was booked to the old song's row, and the queue still pointed at the old song. ADR 0025 listed this as a known gap. Audit finding S2.

**Decision.** `QueueAwarePlayer` overrides both calls and hands them to `PlaybackEngine.seekToWindow`. A window index names a queue entry (the player's window is a slice of the queue around the current song), so a different index is a queue jump: `queue.jumpTo(current + delta)`, the window is refilled, and the row starts as `picked`, exactly like `jumpToUpcoming`. The old row ends as skipped by the usual rule. The same index is a plain seek inside the current song and logs nothing. A position given with the seek is honoured after the refill.

**Consequences.** Extends 0025; its "known gap" about window-index seeks is closed. A controller that jumps backwards also logs `picked`, which is right: the listener chose that song. `MediaItemMapper` became `open` so the JVM test can run the engine without Android's `Uri`.
