# Phone-testing fixes (manual checks)

Six fixes, one commit each (items 3, 4 and 6 share the queue sheet and are one commit). Nothing was compiled or run when written: first `./gradlew check`, then these on the phone.

## 1 Playlist title and menu
- [ ] A very long playlist name shows at most 2 lines with an ellipsis and never runs under the "…" icon.
- [ ] Same on the smallest width you have (or system font size at maximum); Rename and Delete still open.

## 2 Search: add to queue
- [ ] Top result (a song), Songs rows and "See all" songs each have a "…" button with Play next, Add to queue, Add to playlist.
- [ ] Play next puts the song right after the current one; Add to queue puts it after the Play next ones.
- [ ] Album and artist results have no queue menu (their pages' songs do).

## 3 Queue: swipe to remove
- [ ] Swipe an up-next row either way: it disappears like a playlist row. Same on a dimmed played row.
- [ ] Removing an up-next song while playing: no gap, no restart (gapless). Manual ("Queued") and mix songs both go.
- [ ] Removing a played song never changes what is playing.
- [ ] Two copies of one song next to each other: removing the first leaves the second visible.

## 4 Undo (queue and playlists)
- [ ] After a swipe, a snackbar "Removed … " with Undo shows, above the tab bar, and inside the Now Playing sheet.
- [ ] Undo puts the song back at its original place (queue: up next and played; playlist: same position).
- [ ] Remove A then B: only B can be undone; the snackbar for A is gone. It also goes away by itself after a few seconds.
- [ ] Undo after the queue moved on (a song ended meanwhile): the song returns without changing what plays.

## 5 Shuffle first
- [ ] Album, artist, playlist, Recently added, Top 100 and the search Top result (artist, album): Shuffle is filled and on the left, "Play in order" is tonal.
- [ ] A song Top result still shows one filled Play. Tapping a song in an album plays the album in tagged order.

## 6 Queue history
- [ ] Open Now Playing after a few songs: it opens on the playing song; scroll up shows played songs, dimmed, oldest first.
- [ ] Tap a played song: it plays, nothing leaves or reorders in the queue. Up next stays on screen.
- [ ] **Shuffle on:** play 4–5 songs, open the queue: the played list is the order you heard them, not the original order. Toggle shuffle off and on: played songs stay; only up next changes.
- [ ] A song added with Play next / Add to queue and already played appears among the played ones.
- [ ] A long session (200+ songs played, or Shuffle all and skip a lot): the sheet opens fast and scrolls smoothly; only the last 200 played show.
- [ ] Remove every song (swipe all, even the last one in view): the sheet says nothing is playing; Undo brings the last one back.
- [ ] Removing the playing song is not in the UI yet (no swipe on the header): skip it if you cannot do it.
