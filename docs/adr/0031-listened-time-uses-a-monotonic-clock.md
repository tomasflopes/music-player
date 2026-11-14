# 0031: Listened time is measured on a monotonic clock; started_at stays on the wall clock

Date: 2026-11-14 · Status: accepted

**Context.** `PlayEventTracker` used one clock, `System.currentTimeMillis`, for both the row's `started_at` and the time spent listening. The wall clock can be set by the user, corrected by the network or moved by a time-zone change mid-song. A forward jump could turn a skip into "more than 80 % = completed"; a backward jump was clamped to 0 and could turn a long listen into an early skip (-2). Audit finding S1.

**Decision.** The tracker takes two injected clocks. The wall clock stamps `started_at` (it is a date, and the scorer's decay reads it). A monotonic clock times how long a song played; the default is `System.nanoTime()` in milliseconds, injected so tests can drive both. The checkpoint file (ADR 0025) keeps wall-clock stamps only, because a monotonic reading does not survive a process death.

**Consequences.** `listened_ms` is immune to clock changes. `started_at` can still be wrong when the wall clock is wrong, which the scorer already tolerates (future rows count at age 0). Tests pass two lambdas instead of one.
