# 0059: Phone analysis is one unique batch worker, no foreground service

Date: 2026-11-14 · Status: proposed

**Context.** Few songs are new at a time, but with no import the whole library falls to the phone. Background jobs have time limits.

**Decision.** One unique WorkManager job runs `AnalysisBatchRunner` (pure, in core) in batches of about 9 minutes, then enqueues itself again. It starts after every scan and after an import. Up to 100 songs waiting: no constraints. Above that: requires charging (also the whole-library fallback). No foreground service, no notification. Progress shows in Settings.

**Consequences.** Survives process death and job limits; the big fallback run is slow. If the spike (0054) says hours of work, revisit with the `mediaProcessing` foreground service type.
