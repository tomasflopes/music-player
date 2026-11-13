# 0013: Onion rules are written once and run by every module

Date: 2026-11-13 · Status: accepted

**Context.** The only ArchUnit test lived in `:core:model` and forbade Android and the outer layers there. Nothing stopped `:core:search` from using `:core:taste`, `:core:tags` from importing Android, or `:data` from reaching into `:playback`, although CLAUDE.md requires an onion where `:core:*` is pure Kotlin and the outer modules depend inward.

**Decision.** The rules live once in `:core:model` test fixtures (`OnionRules`). Each of `:core:model`, `:core:tags`, `:core:search`, `:core:taste`, `:data` and `:playback` has a one-line `ArchitectureTest` that runs them against its own package. Core modules: no `android..`/`androidx..`, no outer layer, no sibling core module except `:core:model`; `:core:model` depends on no other core module. `:data` and `:playback`: no sibling outer layer and no `:app` packages. The importer fails when a package has no classes, so a renamed package cannot pass vacuously. The old rule is kept, not weakened.

**Consequences.** `:core:analysis` has no classes yet, so it has no test; add one with its first class. `:app` may depend on everything, so it has no rule. Not verified here: `./gradlew check` has to confirm the rules pass on the real class files (the imports were checked by search only).
