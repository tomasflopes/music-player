# 0075: Radio exploration is a small share drawn from a band of the nearest songs; radio from an artist

Date: 2026-11-16 · Status: proposed · Extends 0072

**Context.** On the phone, Radio v0 (0072) felt too random: 30 % of a batch was drawn from every profiled song in the library, and some picks had nothing to do with the seed. The artist page also had no way to start a radio.

**Decision.**
- *Share and band:* `RadioConfig.explorationShare` goes from 0.30 to 0.12 (`nearestShare` 0.88): of the 24 songs after the seed, 21 are nearest and 3 exploration. Exploration is drawn at random only from the `explorationBand` (150) nearest candidates, ranked as the nearest part is (distance plus the recent penalty), never from the whole library. The band must hold a batch (`explorationBand >= batchSize`).
- *Artist radio:* "Start radio" on the artist page (no long-press menu exists on artist rows, so none was added). The seed is the artist's sound: `SoundSpace.centroid` of the songs whose main artist is the artist and that have a profile, no seed song.
- *Own songs:* they may appear, under the existing cap of 3 per main artist, so the artist is never more than 3 of 25. With fewer than 3 profiled songs the radio still runs (one song is its own point, two are averaged); with none it plays nothing and shows "No sound profiles for this artist yet".
- *Shared rules:* `ArtistRadioPolicy` and `RadioPolicy` (`:core:taste`, pure Kotlin) share one `RadioBatchBuilder`: batch 25, recent penalty, one song per duplicate key, 4-song artist gap, seeded determinism, `PlaySource.RADIO`, `picked = true`. No refill, no saving, no schema change. The use case is `StartArtistRadio` in `:app`.

**Consequences.** Surprises stay in the seed's neighbourhood (about 1 % of a 14k library); a small library makes the band cover most of it. Retuning is a config change. Not accepted until `check` and the phone checks pass.
