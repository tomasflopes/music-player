# 0030: Artist image overrides and lookups live in Room

Date: 2026-11-14 · Status: accepted

**Context.** The owner can fix a wrong photo per artist, and the app must not ask Deezer again on every scroll. Both are small per-artist records keyed by the tag artist id (ADR 0026).

**Decision.** Database version 2, hand-written non-destructive migration `MIGRATION_1_2` with two new tables: `artist_image_override (artist_id PK, kind, value, updated_at)` with kinds `remote` (a chosen Deezer URL), `local` (a file name) and `generated`; and `artist_image_lookup (artist_id PK, picture_url NULL, checked_at)`, where a null URL means "not found". Found lookups are trusted 30 days, not-found ones 7 days; failures (offline, quota, malformed reply) are never stored. A gallery pick is copied once to `files/artist_images/<id>-<time>.img` (unique name, so the image caches never serve a replaced picture); the file is deleted when the override is replaced or reset. No persistent URI permission is taken. DataStore was rejected: overrides are keyed per artist and grow with the library, and Room already holds artist ids.

**Consequences.** The log tables are untouched. Downloaded photos are Coil's disk cache (50 MB under `cache/`), which the system may clear; lookups survive and the image is fetched again.
