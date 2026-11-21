# 0084: Skips of songs the app chose count a share of their weight (`skipShare`, default 0.3)

Date: 2026-11-21 · Status: proposed

**Context.** A skip in a mix or a radio is weaker evidence than a skip in a list the owner built: the app picked the song, and the owner may be skipping to move on, not to reject it. Design §7.4 and the mix plan (decision 11) call for the global effect of mix skips to be ×0.3; radio rows have no `mix_id`, so they are keyed on the source. Mix learning (slice 3, ADR 0085) also needs a way to score a mix's own rows at full weight.

**Decision.**
- `TasteConfig.skipShare` (default 0.3, validated finite and in 0..1). In `TasteScorer.scoreSongs` it multiplies the end weight of a row **only when that weight is negative** and the row's source is in the app-chose-it set, after the sign is known and before `Tally.add`. The start bonus, positives and everything else are untouched.
- One shared set, `APP_CHOSEN_SOURCES = {MIX, RADIO}` (internal to `:core:taste`), now used by both `SignalRules` (completion counts as a vote) and the scorer. It is not extended to shuffle: the owner chose to shuffle (ADR 0008).
- Burst softening (×0.1) stacks with the share (a quick mix skip in a burst is -2 × 0.1 × 0.3). The rule "a completed play forgives that song's earlier skips" is unchanged; it resets the negative tally, which already holds the scaled values.
- A `TasteScorer` built with `skipShare = 1.0` scores every row at full weight. The mix score board uses one (ADR 0085).

**Consequences.**
- Scores are recomputed from the log, so past mix and radio skips are re-scored on first run: Top 100 and Rewind (`PastScorer`) shift only for songs with mix or radio skips. Rows from any other source score exactly as before (tested against a share-1.0 scorer, and the existing audit tests run unchanged).
- A `PlaySource.MIX` or `RADIO` row of a song the owner picked and skipped mid-way keeps its +3 start bonus and gets 0.3 of the -1.
- The share is global and one number for mix and radio; separate values would need a second config field.

**Tests.** `TasteScorerSkipShareTest` (scaling, positives, other sources, share 1.0, burst plus share, forgiveness, Rewind and Top 100 on a synthetic log) and `TasteConfigAuditTest` (validation, default).
