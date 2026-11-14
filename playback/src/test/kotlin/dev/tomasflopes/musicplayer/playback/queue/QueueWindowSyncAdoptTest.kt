package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.playback.PlaybackConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The player that takes over at a crossfade holds one song; `adopt` grows its window without restarting it. */
class QueueWindowSyncAdoptTest {
    private class OneSongPlayer(
        song: SongRef,
    ) : QueueWindowSync.PlayerItems {
        val items = mutableListOf(song)
        var currentIndex = 0
        val log = mutableListOf<String>()

        override val count: Int get() = items.size
        override val index: Int get() = currentIndex

        override fun setAll(
            refs: List<SongRef>,
            startIndex: Int,
        ) {
            items.clear()
            items += refs
            currentIndex = startIndex
            log += "setAll"
        }

        override fun append(refs: List<SongRef>) {
            items += refs
            log += "append"
        }

        override fun removeRange(
            from: Int,
            to: Int,
        ) {
            repeat(to - from) { items.removeAt(from) }
            log += "removeRange"
        }

        override fun removeFirst(count: Int) {
            repeat(count) { items.removeAt(0) }
            currentIndex -= count
            log += "removeFirst"
        }

        override fun prepend(refs: List<SongRef>) {
            items.addAll(0, refs)
            currentIndex += refs.size
            log += "prepend"
        }
    }

    private val config = PlaybackConfig(windowAhead = 3, windowBehind = 2)

    @Test
    fun `adopting a song in the middle of the queue fills both sides and never restarts it`() {
        val queue = queue("ABCDEFGHIJ", startIndex = 5)
        val player = OneSongPlayer(queue.current!!.ref)

        QueueWindowSync(queue, player, config).adopt(queue.current!!.ref)

        assertEquals(queue.window(2, 3).map { it.ref }, player.items)
        assertEquals(2, player.currentIndex)
        assertTrue(player.log.none { it == "setAll" }, player.log.toString())
    }

    @Test
    fun `adopting the first song only grows the tail`() {
        val queue = queue("ABCDEFGHIJ")
        val player = OneSongPlayer(queue.current!!.ref)

        QueueWindowSync(queue, player, config).adopt(queue.current!!.ref)

        assertEquals(
            "ABCD",
            player.items
                .map {
                    it.songId.value
                        .toInt()
                        .toChar()
                }.joinToString(""),
        )
        assertEquals(0, player.currentIndex)
        assertEquals(listOf("append"), player.log)
    }

    @Test
    fun `adopting the last song only grows the front`() {
        val queue = queue("ABCDE", startIndex = 4)
        val player = OneSongPlayer(queue.current!!.ref)

        QueueWindowSync(queue, player, config).adopt(queue.current!!.ref)

        assertEquals(
            "CDE",
            player.items
                .map {
                    it.songId.value
                        .toInt()
                        .toChar()
                }.joinToString(""),
        )
        assertEquals(2, player.currentIndex)
        assertEquals(listOf("prepend"), player.log)
    }

    @Test
    fun `after adopting, the next auto advance slides the window like any other`() {
        val queue = queue("ABCDEFGHIJ", startIndex = 2)
        val player = OneSongPlayer(queue.current!!.ref)
        val sync = QueueWindowSync(queue, player, config)
        sync.adopt(queue.current!!.ref)

        queue.advanceOnTrackEnd()
        player.currentIndex++
        sync.onPlayerAdvanced()

        assertEquals(queue.window(2, 3).map { it.ref }, player.items)
    }
}
