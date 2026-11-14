# 0033: Previous that restarts the song ends the row as stopped and starts a nav row

Date: 2026-11-14 · Status: accepted

**Context.** ADR 0025 says previous logs `nav` when it moves, and that a previous which only restarts the song (more than 3 s in, or nothing before it) logs nothing. The row then kept accumulating across the replay, so `listened_ms` could exceed the song's duration, and a row over the duration counts as completed whatever ended it. Audit finding S9.

**Decision.** A previous that restarts the song ends the current row as `stopped` with the time listened so far, then starts a new row for the same song with `started_by = nav`. Stopped, not skipped: the listener did not reject the song, so it takes no negative weight. Each replay is its own play, as with repeat one.

**Consequences.** Extends 0025; the "logs nothing" clause is replaced. A listener who taps previous at 4 s several times produces several short stopped rows; they score 0 unless a row reaches more than 80 % of the song. `listened_ms` can no longer pass the duration through restarts.
