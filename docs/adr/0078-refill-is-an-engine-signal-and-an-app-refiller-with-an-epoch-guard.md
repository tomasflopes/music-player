# 0078: Refill is an engine signal and an `:app` refiller, guarded by an epoch

Date: 2026-11-20 · Status: proposed

**Context.** A mix played a fixed batch and stopped; a song chosen in Search queued the whole result list. Both need more songs appended while the queue plays, without slowing the first sound and without work at start-up.

**Decision.** `PlayQueue` counts an `epoch` (each `replace` and `restore`) and publishes a `RefillSignal(seed, nonManualAhead, queued, lastArtists, epoch)` only for a seeded queue with repeat off. The seed (`RefillSeed.Mix(id)` or `.Song(id)`) is that of the last non-manual entry. `appendBatch(songs, seed, epoch, source)` is a session command; the engine runs it on the serialised edit path, drops it when the epoch or seed no longer match, drops songs already queued, and calls `notifyModes()` (0077). `QueueRefiller` in `:app` collects only the signal, picks the `RefillSource` that handles the seed (Hilt `@IntoSet`), and builds a batch when `nonManualAhead <= lowWater`; a newer signal cancels a build in flight, and a seed that gave nothing is not retried in the same epoch. All library reads run on `Dispatchers.Default`. `QueueCodec` goes to v4 (solo-seed table plus a per-entry number); a saved v3 queue is dropped once.

**Consequences.** Nothing runs until a seeded queue needs a batch; a batch built for a queue that was replaced cannot land. A new kind of seed is one new source. Repeat on means no refill. Not run: `check` has not run on this change.
