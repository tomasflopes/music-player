# 0098: A beat match asks a `TempoReliability` rule, and the slice 1 and 2 rule stays as an implementation

Date: 2026-11-23 · Status: proposed · Extends 0092, 0094

**Context.** `BeatMatchTechnique` read `tempoConfidence >= minConfidence` inline (0.5, a guess). On the owner's library that passes 53 of 14,165 songs, so almost every transition is a plain crossfade. The rule has to change from data (ADR 0099), and CLAUDE.md asks that a decision point sits behind an interface, with the current implementation kept so both can be compared.

**Decision.** `TempoReliability` (`fun interface`, `:core:model`, `isReliable(profile, config)`) decides whether a song's stored tempo can be trusted for a beat match. `BeatMatchTechnique` takes one in its constructor and asks it for both songs; `isUsable` still runs first. `StoredConfidenceReliability` is the slice 1 and 2 rule unchanged (`minConfidence`, 0.5). `CalibratedConfidenceReliability` is the new rule (`calibratedMinConfidence`, ADR 0099) and the default. `PlaybackModule` provides the one `TempoReliability` and passes it to the technique; the Transition lab reads the same instance for its "why a crossfade" line. Both thresholds are `TransitionConfig` fields; Settings gets no entry. A contract (`TempoReliabilityContract`) runs against both implementations in `check`.

**Consequences.** Swapping the rule is one line in `PlaybackModule`, so the old and the new gate can be compared in the lab. A rule that needs more than one profile or a signal that is not in `TransitionProfile` (for example the candidate scores) will need that data added to `TransitionProfile` first; none is needed now. No schema change, no new `profiler_version`, no Mac re-run. Not accepted until `check` and the phone checks pass.
