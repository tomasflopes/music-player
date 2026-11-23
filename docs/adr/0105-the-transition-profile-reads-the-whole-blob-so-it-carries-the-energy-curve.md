# 0105: The transition profile reads the whole BLOB, so it carries the energy curve

Date: 2026-11-23 · Status: proposed · Extends 0069, 0092, 0065

**Context.** `TransitionProfile` was made from the first 132 bytes of the stored profile (ADR 0069, 0092), which hold the 33 fixed floats. The talk rule of ADR 0104 needs the energy curve, which follows them (ADR 0065).

**Decision.** `TransitionProfile.energyPerSecond` (a `List<Float>`, empty by default) holds the curve. `TransitionProfiles.fromBlob` reads a whole BLOB (null for bytes that cannot be a profile or numbers that cannot be a grid); `fromFixed` is unchanged and leaves the curve empty. `SoundProfileDao.soundBlob` returns the whole `features` BLOB of a current, in-library profile under the same rules as `soundHead`, and `RoomTransitionProfileSource` uses it. `soundHead` stays for its tests. The sound space of ADR 0069 still reads only heads.

**Consequences.** No schema change, no new `profiler_version`. A transition read is about 1 KB instead of 132 bytes (a 4 min song has 240 floats of curve), twice per planning and off the main thread, so nothing is measured to matter. The beat match ignores the new field. The Room reader test in `src/androidTest` is unchanged and not run here.
