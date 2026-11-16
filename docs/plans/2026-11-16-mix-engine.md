# Plan: Phase 4 mix engine (design 7.6)

Status: approved by the owner on 2026-11-16, nothing implemented yet. ADR numbers 0076 to 0080 are reserved for it
(0071 is reserved by ADR 0070, 0074 was used and reverted).

## Goal
A mix = a name + 1 to 5 seeds (songs and/or artists) + learned state. It builds batches of 25, refills when 5 or fewer
songs remain, learns from skips and plays, and sits in a "Your mixes" row on Home, above the dynamic lists. Radio
(ADR 0072, 0075) is the temporary version and keeps `RadioBatchBuilder` as the shared core.

## Decisions
1. **Query point.** Anchors, not one centroid. Anchors = song seeds, the centroid of an artist seed (ADR 0075) and, from
   slice 3, up to about 15 songs with a positive mix score. Rank key (lower is better) = distance to the nearest anchor
   divided by that anchor's 50th-neighbour distance (density normalised), plus a proximity penalty for strongly
   disliked songs, minus small weights for mix score and global score; the builder adds the recent penalty. The rule
   sits behind a small `MixQuery` interface: `AnchorQuery` (default) and `CentroidQuery` (kept so both can be
   benchmarked, CLAUDE.md decision points). Amends the design wording "centroid".
   Before slice 1 is final: compare both rules on the owner's real seed sets with the ADR 0073 tools.
2. **Persistence.** Tables `mix(id, name, created_at, updated_at)` and `mix_seed(mix_id, position, kind, ref_id)`,
   primary key `(mix_id, kind, ref_id)`, cascade on mix delete, no foreign key to `song` or `artist` (ADR 0037).
   Schema 6 to 7, `MIGRATION_6_7`, exported `7.json`. **No `mix_song_score` table in v1**: mix state is a pure function
   of the `play_event` rows with that `mix_id` (ADR 0023 precedent: on demand first, cache if it measures slow).
   Deviates from design 7.6.
3. **Mix id on every play.** `QueueEntry.mixId`, `PlayRequest.mixId`, one more `PendingPlayStore` field and
   `QueueCodec` v3 (as playlistId, ADR 0042). `play_event.mix_id` already exists; no log schema change. Needed in
   slice 1 because the log is append-only and cannot be attributed afterwards.
4. **Refill.** `PlayQueue.append(songs, source, mixId)` adds non-manual entries at the tail and extends
   `originalOrder`; `PlaybackController.appendBatch` is a new session command on the same serialised edit path as
   `addToQueue`. `MixSession` in `:app` watches playback state; when the playing entry has a `mixId` and 5 or fewer
   non-manual entries remain it builds and appends a batch. Candidates already in the queue are dropped; the builder
   gets the last 4 queued artists as preceding songs for the gap rule. Skips are read from the existing
   `PlayEventSource`, filtered by `mixId` in memory, one snapshot per refill, after a short delay (about 2 s) so the
   previous row is committed. No new port or index unless measured.
5. **No `CandidateSource` multibinding yet.** One function produces `List<Candidate>`. Add the extension point when
   sound-based playlist suggestions (phase 2 7.13) are the second consumer.
6. **Save as.** Save as playlist from radio and mixes through the existing `SaveAsPlaylist` (ADR 0044); verify it takes
   the whole queue, since `queueContent()` is "from the current song on". Save as mix from radio: the radio's seed plus
   up to 4 songs played through, trimmed in a dialog; learning starts fresh. "Create mix from selection" (3 to 5 songs)
   in the existing multi-select covers Discover, Rewind, Top 100 and playlists.
7. **Seeds per mix.** Domain accepts 1 to 5; the create flow asks for 3 to 5; Save as mix from radio may give fewer,
   with a hint to add more.
8. **Home.** `MixesSection : HomeSection`, bound `@IntoSet` beside `PlaylistsSection`, above the dynamic lists.
   `DynamicListProvider` cannot hold user mixes.
9. **Tempo stays out of the sound vector.** It is folded to 60 to 180, often half or double for hip-hop, and its
   confidence is not calibrated (phase 3 hand-over); a BPM dimension would add noise, and changing the vectorizer is
   outside this plan. A per-mix tempo filter is a later idea if a gym mix needs it.
10. **Exploration.** 30 % falling linearly to 10 % over about 200 mix rows (any outcome), counted from the log. Drawn
    from a band of the 600 nearest candidates (radio keeps 150). Slices 1 and 2 use a fixed 30 %.
11. **Global skip share.** Skips inside a mix count x0.3 on the global score. Radio skips are also less penalising:
    x0.3 as well, through a separate `TasteConfig` value (default 0.3) keyed on `PlaySource.RADIO`, since radio rows
    have no `mix_id`. Only negative contributions are scaled. Scores are recomputed from the log, so past radio skips are
    re-scored too (Top 100 and Rewind can shift slightly on first run); rows with neither a `mix_id` nor source `radio`
    score exactly as before. Cover both facts in the `TasteScorer` tests.

## Slices (one commit and one ADR each)
1. **ADR 0076, mixes exist.** Tables, schema 7, repository, create/edit/delete, minimal "Your mixes" row, start = one
   batch of 25 through `MixQuery` over `RadioBatchBuilder`, mix id through queue and log. No refill, no learning.
2. **ADR 0077, refill.** `PlayQueue.append`, `appendBatch`, `MixSession`, dedupe and seam gap.
3. **ADR 0078, learning.** `MixScoreBoard` (filter plus the existing `TasteScorer`), liked anchors, disliked penalty,
   exploration schedule, x0.3 skip share for mixes and radio in `TasteScorer`/`TasteConfig`.
4. **ADR 0079, edges.** Debug screen per mix in the debug source set (skip rate per week, exploration ratio), Save as
   playlist and Save as mix from radio and mixes, Create mix from selection.
5. **ADR 0080, radio refill (later, optional).** Radio as an unsaved mix: refill and session-only learning from rows
   with source `radio` since the radio started. Hold until slices 1 to 3 have been checked on the phone.

## deviceCheck for slice 1
Migration 6 to 7 keeps playlists, `play_event` and `sound_profile` rows; seed replace is atomic; delete cascades; a seed
whose song left the library is hidden, not removed; `mix_id` round-trips through `play_event`; `LogIsAppendOnlyTest`
stays green.

## Not read when this plan was written (confirm at the start of the slice that touches it)
`ArtistPicking.spreadByArtist` (preceding songs), `PlaybackState`/`QueueContent` (exposing `mixId`), `HomeSection`
order, `SaveAsPlaylist`, `QueueCodec`/`PendingPlayStore`, `SignalRules` for the `MIX` source.

## Open risks
- The anchor rule is untested on real seeds; the comparison above is the first task of slice 1.
- Refill reads the log once per refill; measure on the full log before adding a `mix_id` index.
- Weights and radii live in a `MixConfig`; starting values, to be tuned with the debug screen.
