# Design: refill (mix refill + solo-song extension)

Status: approved by the owner on 2026-11-20. Not implemented. Slice 2 of docs/plans/2026-11-16-mix-engine.md plus the solo extension. ADR numbers: 0078 to 0080 (0081 conditional); the numbers in the mix plan are stale.

## Goal
A mix plays a batch of 25 and appends 25 more when 5 or fewer non-manual entries remain. A single song tapped in Search plays at once and gets about 5 songs that sound like it, refilled the same way at 2 or fewer left. One mechanism serves both.

## Non-goals
Learning from skips (slice 3), the x0.3 skip share, Save as, radio refill, any change to Songs tab, albums, playlists, lists, Shuffle all, artist shuffle, or to StartRadio/StartMix (finding D3 there stays a separate fix).

## Anti-goals
Slower tap to first sound. Work at app start. Appended songs counted as picked. Refill growing a looping queue.

## Design
- Engine publishes `PlaybackState.refill: RefillSignal?` only when a seed exists: seed, non-manual entries ahead, ids already queued (played included), last 4 queued artists, queue epoch (counts replace and restore). It republishes on queue events only (ADR 0077).
- Seed on every auto entry (`QueueEntry.seed`: `Mix(id)` | `Song(id)`), the tapped song included; manual entries have none. The signal's seed is that of the last non-manual entry in the queue, so a manual song playing never ends the queue.
- `QueueRefiller` in :app collects only the small signal; heavy dependencies are `dagger.Lazy`; nothing is read until a signal with a seed arrives. All work on Dispatchers.Default. A seed with no sound profile is tried once per epoch.
- Triggers when: repeat is OFF, the feature applies (solo needs the setting "Extend single songs", default on, read on each solo tap and each refill; mixes ignore it), and non-manual ahead <= low-water (solo 2, mix 5; `SoloExtensionConfig`, `MixConfig.refillLowWater`, both validated: low-water < batch).
- Single-flight. It sends `appendBatch(songs, seed, epoch, source)` on the serialised edit path. The engine drops it if the epoch moved, drops songs already queued, appends non-manual entries at the tail, extends originalOrder, and calls `notifyModes()` itself (an edit outside the window does not announce itself otherwise).
- Mix seed: MixEngine (reads the mix at refill, so edits apply; a deleted mix does nothing). Song seed: the song-radio path, batch 5 non-seed songs, 88/12 nearest/exploration; the anchor stays the chosen song.
- RadioBatchBuilder gains `exclude: Collection<Song>` (songs, so duplicate keys of queued copies are caught) and `precedingArtists` (last 4) for the gap rule.
- Solo start: a tap on a song in Search results, a Songs-section or See-all row, or the top card's Play on a song becomes PlayRequest([song], source SEARCH, picked, seed Song). It no longer queues the other results. The extension comes after the song has started.
- Logging: tapped song stays `search`, picked. Appended solo songs `radio`, picked = false (StartedBy AUTO/NAV). Mix batches `mix` with the mix id. Completions of radio and mix count +1 (SignalRules); no other source rule exists in core/taste.
- Persistence: QueueCodec v4 adds a table of solo-seed song ids and a short number per entry (-1 none). A mix seed is derived from mixId. A saved v3 queue is dropped once.
- Dedupe against the whole queue, played included. Manual entries stay before auto songs; tapping another song replaces the auto songs, pending manual entries stay.

## ADRs
0078 refill mechanism + QueueCodec v4 + epoch guard + signal; 0079 appended songs log as radio/mix, never picked; 0080 solo extension (definition, numbers, setting, builder exclude and preceding artists); 0081 only if the phone shows refill is slow: candidate pre-selection through songsByIds.

## Approaches considered
(a) Refiller in :app watching a small engine signal (chosen); (b) refill inside PlaybackEngine, rejected: it would pull MixEngine and the sound space into :playback and break the onion; (c) refill from SearchViewModel/StartMix, rejected: dies with the screen and misses restored queues.

## Left to the builder
Exact class and file names, test layout, the Settings row wiring, how the signal is computed cheaply in `publish()`.
