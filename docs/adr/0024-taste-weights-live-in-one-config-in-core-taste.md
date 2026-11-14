# 0024: Taste weights live in one `TasteConfig` in `:core:taste`

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.4 gives the signal weights, the browsing-burst rule and the decay half-life. They will be tuned against the owner's real listening, so they must sit in one place and the scorer must not hold literals.

**Decision.** `TasteConfig` (a plain Kotlin data class in `:core:taste`, no annotations) holds `SignalWeights`, `BurstRule`, `DecayRule` and `TopListRule`, with the design's numbers as defaults: picked +3, completed +1, skipped under 15 s -2, skipped under half -1, burst of 3 or more quick skips softened to x0.1, half-life 30 days. `:app` provides one instance with `AppModule.tasteConfig()`; `:data` passes it to the scorer, the board and the provider. The scorer, the burst rule and the provider take the config as a parameter, so tests build their own. It is not a Settings entry.

**Consequences.** Tuning is a one-file edit and a rebuild. A phone-editable config would need persistence and a recompute policy for old values; add it only if rebuilding to tune becomes a chore. The rule details (what counts as completed, which songs get the completed bonus) are in `SignalRules` and covered by its tests.
