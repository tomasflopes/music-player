# 0086: Mix and artist radio batches are shuffled in a batch step, not through the player's shuffle toggle

Date: 2026-11-22 · Status: proposed

**Context.** A mix and an artist radio opened with the same song every time. The builder puts the opening songs first (2 per artist seed in a mix, 3 for an artist radio, ADR 0075, 0076), then orders the rest nearest-first; the artist spread keeps that rank order, and the only random part (exploration) never reaches the head. The same seeds gave the same first song and nearly the same order.

**Decision.**
- **A batch step, `BatchOrdering`** (`:core:taste`, `shuffle` package): `order(batch, precedingArtists, random)`. Production is `ShuffledBatchOrdering` over `SmartShuffle.orderAfter` (ADR 0035, gap from the config); `BatchOrdering.Identity` keeps the builder's order. `MixEngine` and `ArtistRadioPolicy` take it as a constructor argument and apply it to the finished batch, after selection.
- **Selection does not change.** The same songs are picked (opening songs, nearest, exploration, banned, cool-down, per-artist cap, learning). Only the order changes. `RadioBatchBuilder` is untouched, so the song radio is untouched.
- **Not the player's shuffle toggle.** The batch is ordered before it is queued, so Up next, drag editing (ADR 0081) and `QueueCodec` see a plain queue and behave as today; the toggle stays off and a later toggle press still means what it meant.
- **Song radio keeps the seed first.** That is its definition (ADR 0072). `RadioConfig.shuffleBatch` governs the artist radio only.
- **Full shuffle of the batch.** The opening songs are shuffled in with the rest, so the seed artist's songs are in the batch (same count) but no longer guaranteed first. This amends the "first song is the artist's" rule of ADR 0075 and the "open the batch" rule of ADR 0076.
- **Refill.** Each appended batch is shuffled within itself with `precedingArtists` (the last queued artists) as the preceding songs, so the gap also holds across the seam. Queued songs are never reordered and manual entries are never touched.
- **Config.** `MixConfig.shuffleBatch` and `RadioConfig.shuffleBatch`, default true; false gives exactly the old order. A fresh `Random` is made at every start and every refill (`config.seed`, when set, fixes it for tests and debugging).

**Consequences.**
- The golden tests (`an empty learning gives exactly the slice 2 batch` for `pick` and `refill`, the radio lists) pin the order, so they run with `BatchOrdering.Identity` and still prove the selection did not change.
- When the artists cannot be spaced (few artists), `SmartShuffle` relaxes the gap as it does for the player's shuffle.
- A mix no longer opens with its nearest song; if the owner wants "starts closest, then drifts", the switch is off for that case.

**Tests.** `BatchOrderingTest`, `MixEngineShuffleTest` and `ArtistRadioShuffleTest` (permutation of the unshuffled batch, more than one distinct first song over 100 seeds, gap 4 inside the batch and across the refill seam, queued entries untouched, `shuffleBatch = false` equals the old order, a seeded random is reproducible, seed artist's songs still in the batch), `MixConfigTest` (defaults).
