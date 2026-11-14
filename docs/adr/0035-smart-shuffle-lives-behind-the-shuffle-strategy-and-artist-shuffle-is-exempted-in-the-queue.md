# 0035: Smart shuffle is a `ShuffleStrategy`; the queue exempts artist shuffle and passes what precedes

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.8: every shuffle except artist shuffle keeps songs by the same main artist out of the next 4 tracks, relaxed when the pool is too small; playing in order is never shuffled; Play next and Add to queue are untouched. ADR 0010 gives the queue the shuffle toggle and manual entries, ADR 0020 says the queue owns the order, ADR 0024 puts tunable numbers in one config.

**Decision.**
- *Where the rule lives:* `SmartShuffle` in `:core:taste` implements the existing `ShuffleStrategy`; `PlainShuffle` stays. The interface gains `orderAfter(preceding, items, random)`, defaulting to `order`, so a strategy can keep clear of the songs that will already be playing before `items` (the current song and manual entries when shuffle is turned on mid-song, the tapped first song, the end of the previous round on a repeat-all wrap). Existing implementations need no change.
- *The number:* `SmartShuffleConfig(artistGap = 4)` in `core/taste/config`, next to `TasteConfig`, validated with `require` (0..100; 0 turns spacing off). Not part of `TasteConfig`: it holds taste weights.
- *Artist shuffle:* `PlayQueue` takes a second strategy, `artistShuffle`, which `QueueOrdering` uses when the queue's source is `ARTIST_SHUFFLE`, or `ARTIST` about to become it by the toggle (`source.withShuffle(true)`). Hilt binds `SmartShuffle` as the strategy and `PlainShuffle` as `artistShuffle`. The parameter defaults to the main strategy so existing queue tests are unchanged.
- *In order:* nothing changes; `replace` with shuffle off never calls a strategy.
- *Relaxing the gap:* the gap is the smaller of the configured one and the widest that still fits the biggest artist, `(songs - biggest) / (biggest - 1)`, so 3 artists give a gap of 2, one artist gives 0 (plain shuffle). Placement is a weighted random draw among artists outside their cooldown (uniform over eligible songs), except that the artist with the most songs left goes first once skipping it would leave it too little room. If nobody is eligible the artist who has waited longest plays.
- *Manual entries* are never moved. They count as neighbours when the automatic songs are ordered on a toggle or a new play, but a manual entry added later may sit next to a song of its artist.

**Consequences.** One dominating artist lowers the gap for everyone in that shuffle (a library of 40 songs with one artist at 12 gets gap 2). Spacing is best effort when the queue is edited by hand afterwards. The Recently added shuffle (skip-aware, later) and playlists will implement the same interface. Extends ADR 0010; amends nothing.
