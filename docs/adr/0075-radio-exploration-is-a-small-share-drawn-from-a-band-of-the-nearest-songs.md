# 0075: Radio exploration is a small share drawn from a band of the nearest songs

Date: 2026-11-16 · Status: proposed · Extends 0072

**Context.** On the phone, Radio v0 (0072) felt too random: 30 % of a batch was drawn from every profiled song in the library, and some of those picks had nothing to do with the seed.

**Decision.**
- *Share:* `RadioConfig.explorationShare` goes from 0.30 to 0.12 and `nearestShare` from 0.70 to 0.88. Of the 24 songs after the seed, 21 are nearest and 3 are exploration.
- *Band:* exploration is drawn only from the `RadioConfig.explorationBand` (150) nearest candidates, ranked exactly as the nearest part is (distance plus the recent penalty). Songs the nearest part already took are skipped. Nothing outside the band is ever drawn. The band must hold a whole batch (`explorationBand >= batchSize`, checked in the config).
- *Unchanged:* recent window penalty, copies of the seed left out, one song per duplicate key, at most 3 per main artist (seed counted), 4-song artist gap, seed first, seeded determinism, `PlaySource.RADIO`, `picked = true`.
- Every number is in `RadioConfig`; `RadioPolicyTest` checks the share, that no pick falls outside the band, and that one seed gives one batch.

**Consequences.** Surprises are still there but stay in the same neighbourhood as the seed: about 1 % of a 14k library. A smaller library makes the band cover most of it, so the batch is looser there. Retuning is a config change. Not accepted until `check` and the phone checks pass.
