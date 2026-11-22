# 0089: Every play counts the same in Wrapped, and most skipped reuses the taste skip and burst rules

Date: 2026-11-22 · Status: proposed

**Context.** Taste scores weigh a play by how it started and ended, and a skip of a mix or radio song counts a share (ADR 0084). Wrapped is a look-back at what happened, not a taste model, so those weights would hide plays.

**Decision.**
- *Plays:* one `play_event` row is one play with weight 1, whatever its source (shuffle, mix, radio, Discover, Rewind, playlist, manual) and however it ended. The ×0.3 skip share of ADR 0084 does not apply. Artist stats use the main artist (`song.artist_id`, ADR 0026); albums use `song.album_id` and leave out songs without one. Plays of songs that left the library still count; the card drops a song it can no longer name.
- *New discoveries:* a song is a discovery of the period holding its first play in the whole log (`MIN(started_at)` per song), so a song first heard in March is never a discovery in April.
- *Most skipped:* a row is a real skip when it ended as skipped and `SignalRules.outcomeOf` does not call it finished (listened past 80 %). Rows inside a browsing burst, found by `BrowsingBursts` with the `TasteConfig` burst rule, are left out. Neither rule is copied. A song needs `StatsConfig.minSkips` (2) real skips to be listed.
- *Biggest comeback:* the artist with the largest rise in plays against the previous period of the same kind, at least `comebackMinRise` (2); ties go to more plays now, then the lower id. An artist missing before rose from nothing. No plays in the previous period means no comeback.
- *Listening clock:* plays per local hour, from 15-minute buckets mapped through the zone, so daylight saving and half-hour zones are exact.

**Consequences.** A quick skip is a row, so it counts as a play in top songs; a song skipped many times in a browsing burst can rank. Revisit with a minimum listened time if the phone shows it. Bursts that straddle a period edge are split. Periods are the owner's local months and years; the zone is read once per process (the app clock is UTC, ADR 0031).
