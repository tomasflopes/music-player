# CLAUDE.md --- Music Player

Personal offline Android MP3 player. Kotlin, Compose, Media3, Room, Hilt.

## Context sources

Read in order, stopping when sufficient:

1. `docs/adr/INDEX.md` --- find relevant decisions; read only relevant ADRs.
2. `design/2026-11-13-music-player-design.md` --- product spec; §10 defines
   scope and takes precedence over the brief.
3. [Engineering Guidelines](https://claude.ai/artifact/LVxDCUR9B7pcfTBhmvrBva) ---
   architecture, extension points (§2.4), open decisions (§4).
4. `claude/PROJECT_BRIEF.md` --- history, only when needed.

Never read entire documents when targeted searches or sections suffice.

## Architecture

- Apply SOLID and DRY. Keep classes focused and responsibilities separated.
- Onion architecture: `:core:*` is pure Kotlin; `:data`, `:playback`, `:app`
  depend inward and implement core interfaces.
- Extend through small interfaces and Hilt multibindings, not growing `when`
  blocks.
- Isolate OS/phone APIs and performance-critical operations (search, shuffle,
  scoring, scan, analysis) behind Hilt-injected interfaces.
- Preserve existing implementations when introducing alternatives to enable
  benchmarking.

## Engineering rules

- Every engineering decision requires an ADR in `docs/adr/`, following
  Guidelines §3.7 (10--15 lines).
- Never edit accepted ADRs. Supersede them and update the index.
- Contract-test every interface implementation.
- `./gradlew check` runs locally on Mac. No cloud CI.
- Follow the current phase in design §10. No out-of-scope features.

## Implementation

- Inspect existing code before changing it. Re-read only when necessary.
- Prefer targeted searches and minimal file reads. Avoid files over 100KB unless
  necessary.
- Implement sensible defaults without asking permission.
- Ask only about genuine blockers or irreversible decisions without an obvious
  choice. Ask one concise question.
- Create ADRs as files, not chat summaries.
- Do not guess APIs, versions, flags, SHAs, or dependencies. Verify them first.

## Output

- Prioritize implementation over commentary.
- No progress updates, recaps, or explanations of completed changes.
- Finish with at most two lines: changes made and any remaining blocker.
- Be thorough in reasoning, concise in output.
- No sycophancy, emojis, or em-dashes.
