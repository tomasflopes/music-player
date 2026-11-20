# 0079: Appended songs log as `radio` or `mix`, never as picked

Date: 2026-11-20 · Status: proposed

**Context.** Songs the refiller appends were not chosen by the owner. Logging them as `search` or as picked would give them the +3 of a deliberate choice and hide how much of the listening is suggested.

**Decision.** Appended solo songs carry source `radio`, mix batches carry `mix` with the mix id, both `picked = false` (`StartedBy` is never `PICKED` for them). The tapped song keeps source `search`, picked. Completion counts +1 for `MIX` and `RADIO`; the skip share of slice 3 is keyed on `radio`.

**Consequences.** Search-chosen listening and suggested listening stay separable in the log and the stats. Covered at queue level only; the logged source of an appended song is checked on the phone through the debug link.
