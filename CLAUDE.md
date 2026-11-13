# CLAUDE.md — Music Player

Personal, offline Android MP3 player (Kotlin, Compose, Media3, Room, Hilt). Owner: Tomás. Read this file first; it is short on purpose.

## Where context lives (read in this order, stop when you have enough)
1. `docs/adr/INDEX.md` — one line per decision. Open only the ADRs relevant to the task.
2. `design/2026-11-13-music-player-design.md` — product spec. §10 defines phase scope and wins over the brief.
3. Engineering Guidelines doc (https://claude.ai/artifact/LVxDCUR9B7pcfTBhmvrBva) — structure, extension points (§2.4), open technical decisions (§4).
4. `claude/PROJECT_BRIEF.md` — conversation history. Only if the above leave a gap.

Do not re-read whole documents each session. Use search or read the section you need.

## Engineering rules
- **SOLID and DRY.** One class, one job. Extend by adding implementations, not by editing `when` blocks.
- **Onion architecture.** `:core:*` is pure Kotlin with no Android types and depends on nothing outward. `:data`, `:playback` and `:app` depend inward and implement core interfaces.
- **Extensibility.** New behaviour = new implementation of a small interface, registered with a Hilt multibinding (Guidelines §2.4).
- **Decision points.** Anything that talks to an OS driver or phone API, or sits on a performance-critical path (search, shuffle, scoring, scan, analysis), goes behind an interface injected with Hilt. Keep the current implementation when adding an alternative, so both can be benchmarked.
- **ADRs.** Every engineering decision gets an ADR in `docs/adr/` (format in Guidelines §3.7, 10–15 lines). Never edit an accepted ADR; supersede it and update the index.
- **Tests.** `./gradlew check` runs locally on the Mac. No cloud CI. Contract tests run against every implementation of an interface.
- **Scope.** Respect the current phase in design §10. Do not build out-of-scope features.

## Implementation sessions (token budget)
- Output code and file changes only. No progress commentary, no recaps, no explanations of what you just wrote.
- Do not ask permission for choices that have a sensible default. Pick it and continue.
- Stop and ask only for a genuine blocker, or an irreversible decision that could reasonably go either way. One short question.
- Write the ADR for each decision as a file, not as chat text.
- Finish with at most two lines: what changed, and any blocker.
- Read only the files you need. Prefer targeted search over opening whole files.

## Language
Code, comments, ADRs and docs in English. Conversation with the owner in European Portuguese.
