# 0076: Mixes are two Room tables (schema v7) queried by anchors; the mix id rides on every queue entry and log row

Date: 2026-11-16 · Status: proposed · Extends 0037, 0042, 0072, 0075

**Context.** Phase 4 slice 1 (plan 2026-11-16): mixes must exist, start one batch and be attributable in the append-only log. Design 7.6 asks for a score table and a centroid query; neither is needed yet.

**Decision.**
- *Tables (migration 6 to 7, `MIGRATION_6_7`, exported `7.json`):* `mix (id, name, created_at, updated_at)` and `mix_seed (mix_id, position, kind, ref_id)`, primary key `(mix_id, kind, ref_id)`, cascade on mix delete, no foreign key to `song` or `artist` (as 0037). A seed whose song left the library is hidden by readers and keeps its row; replacing seeds keeps hidden rows and runs in one transaction. The domain accepts 1 to 5 seeds, the create flow asks for 3 to 5.
- *No `mix_song_score` table:* a mix's state will be a function of the `play_event` rows with its `mix_id` (on demand first, cache if it measures slow; precedent ADR 0023). Slice 1 has no learning. Deviates from design 7.6.
- *Mix id on queue and log:* `QueueEntry.mixId`, `PlayRequest.mixId`, a tenth `PendingPlayStore` field and `QueueCodec` v3 (a mix table and a number per entry; v1 and v2 saves are ignored once, as in 0042) end in `play_event.mix_id`, which has existed since schema 1. No log schema change; manual entries log no mix.
- *Query:* `MixQuery` in `:core:taste` turns anchor points (song seeds, artist centroids as in 0075) into a rank key. `AnchorQuery` (default): nearest anchor distance divided by that anchor's 50th-neighbour distance. `CentroidQuery` is kept for comparison. `MixEngine` first opens the batch with each artist seed's 2 closest own songs (`openingPerArtist`, counted in the artist cap; an artist's centroid can sit among strangers' songs, and the first phone use showed almost no seed-artist songs), then feeds the key to the radio's `RadioBatchBuilder`: batch 25, exploration fixed at 30 % from the 600 nearest, recent penalty, cap and gap as in 0072. Every number is in `MixConfig`; `RadioConfig` is unchanged.
- *`CandidateSource` is deferred* until a second consumer exists (sound-based playlist suggestions). `StartMix` in `:app` plays one batch as `PlaySource.MIX`, picked, and says so when no seed has a sound profile. Refill, learning and the x0.3 skip share come in slices 2 and 3.

**Evidence.** Synthetic space, seeds in two clusters with a bridge cluster between them: the anchor top 25 is 13 + 12 songs from the two clusters and none from the bridge; the centroid lands in the bridge and its top 25 is all bridge songs; the two lists share no song. Not yet run on real seed sets with the ADR 0073 tools.

**Consequences.** Mixes survive library changes; a retired seed returns with its file. A log older than this build has no mix rows. Not accepted until `check`, `deviceCheck` and the phone checks pass.
