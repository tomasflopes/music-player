# 0060: Mac/phone consistency: generated test files, a golden file and a debug report

Date: 2026-11-14 · Status: proposed · Extends 0003

**Context.** ADR 0003 requires the two decoders to agree within a tolerance. The owner's music cannot go into git.

**Decision.** Commit small generated, licence-free MP3s (click tracks at 90/120/150 BPM, sines, a sweep, noise, silence; CBR and VBR; 44.1 and 48 kHz) and a golden-features file. JVM checks it in `check`, Android in `deviceCheck`. Tolerances: tempo ±1 BPM, other features ±2 % relative (widened if the spike picks seek-decode). A debug "Consistency report" re-analyses 20 imported songs on the phone and shows the largest gap per feature on real music.

**Consequences.** The test files cover the decoders, the report covers real songs. Tolerances are tuned with the first golden run and then frozen in this ADR's successor, not edited here once accepted.
