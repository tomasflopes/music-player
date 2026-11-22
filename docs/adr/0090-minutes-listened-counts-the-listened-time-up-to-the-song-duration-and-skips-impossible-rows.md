# 0090: Minutes listened counts listened time up to the song duration and skips impossible rows

Date: 2026-11-22 · Status: proposed

**Context.** ADR 0025 says rows written by phase 1 builds have unreliable `listened_ms` and `song_duration_ms`: the duration was often 0, and a checkpoint could zero `listened_ms`. Rows of later builds are right. A total that adds the raw values can be far too large (a bad duration, a repeat loop) and cannot be told from a real one.

**Decision.** `ListenedTime.countedMs(listenedMs, durationMs)` in `:core:model`: a row counts 0 when `listened_ms <= 0`, `song_duration_ms <= 0` or `song_duration_ms > 4 hours` (`MAX_SONG_MS`); otherwise it counts `min(listened_ms, song_duration_ms)`. Minutes are the sum divided by 60 000, rounded down. The same rule runs in SQL (`SUM(MIN(listened_ms, song_duration_ms))` with the three conditions) and in the in-memory reference; `ListeningAggregatesContract` checks both. Rows of phase 1 builds with a missing duration are skipped, not guessed.

**Consequences.** The total can be too small, never too large: old rows with no duration add nothing, and a row cut to 0 by a checkpoint adds nothing. A real song over 4 hours would not count; no MP3 in the library is that long. The Minutes card is hidden under one minute. A period made only of phase 1 rows shows no minutes card while its other cards still show.
