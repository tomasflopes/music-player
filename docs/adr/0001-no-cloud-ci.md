# 0001: No cloud CI; check locally before pushing

Date: 2026-11-13 · Status: accepted

**Context.** Personal project; the owner does not want to spend GitHub Actions minutes on it.

**Decision.** No CI. `./gradlew check` runs locally, enforced by a `.githooks/pre-push` hook (`scripts/setup.sh` enables it).

**Consequences.** Nothing stops a push made with `--no-verify`. Revisit if the project gains contributors.
