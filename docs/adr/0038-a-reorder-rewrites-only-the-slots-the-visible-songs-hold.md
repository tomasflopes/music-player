# 0038: A reorder rewrites only the slots the visible songs hold; hidden rows never move

Date: 2026-11-14 · Status: accepted

**Context.** ADR 0037 keeps rows of retired songs in `playlist_song`, so the positions the owner sees have gaps. A drag in the playlist screen hands the store the new order of the songs on screen only.

**Decision.**
- `PlaylistStore.reorder(id, order)` takes the visible songs (`in_library = 1`) with their stored positions, sorts those positions, and gives them to the songs of `order` in that order (`PlaylistOrder.reassign`, pure, in `:core:model`). A retired row keeps its position, so it returns to the same place relative to its neighbours when the file comes back.
- `order` must hold exactly the songs visible now. Otherwise nothing is written (`reassign` returns `null`), so a stale screen cannot lose or invent songs.
- It runs in one transaction. `position` is not unique, so rows can be rewritten one by one.
- Adding appends after the highest position of any row (ADR 0037), so new songs never land in a hidden row's gap.

**Consequences.** Position values are not renumbered to 0..n-1, only permuted; gaps stay forever. `InMemoryPlaylists` uses the same `PlaylistOrder`, so the fake and Room agree. The Room side is covered by `PlaylistDaoTest` on the emulator, not run here.
