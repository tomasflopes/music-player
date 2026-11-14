package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.playback.PlaybackConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueWindowSyncTest {
    /** Records what the sync asks the player to do; the "player" is just a list and an index. */
    private class FakePlayerItems : QueueWindowSync.PlayerItems {
        val items = mutableListOf<SongRef>()
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
            log += "setAll(${refs.size}, $startIndex)"
        }

        override fun append(refs: List<SongRef>) {
            items += refs
            log += "append(${refs.size})"
        }

        override fun removeRange(
            from: Int,
            to: Int,
        ) {
            repeat(to - from) { items.removeAt(from) }
            log += "removeRange($from, $to)"
        }

        override fun removeFirst(count: Int) {
            repeat(count) { items.removeAt(0) }
            currentIndex -= count
            log += "removeFirst($count)"
        }

        override fun prepend(refs: List<SongRef>) {
            items.addAll(0, refs)
            currentIndex += refs.size
            log += "prepend(${refs.size})"
        }
    }

    private val config = PlaybackConfig(windowAhead = 3, windowBehind = 2)

    private class Rig(
        val queue: PlayQueue,
        val player: FakePlayerItems,
        val sync: QueueWindowSync,
    ) {
        operator fun component1() = queue

        operator fun component2() = player

        operator fun component3() = sync
    }

    private fun setUp(size: Int = 20): Rig {
        val queue = queue(('A'..'Z').take(size).joinToString(""))
        val player = FakePlayerItems()
        return Rig(queue, player, QueueWindowSync(queue, player, config))
    }

    private fun advanceTo(
        rig: Rig,
        steps: Int,
    ) = repeat(steps) {
        rig.queue.next()
        rig.player.currentIndex++
        rig.sync.onPlayerAdvanced()
    }

    @Test
    fun `removing a played song swaps the songs before the current one and never refills`() {
        val rig = setUp()
        rig.sync.fill()
        advanceTo(rig, 3)
        rig.player.log.clear()

        rig.queue.remove(1)
        rig.sync.onQueueEdited()

        assertEquals(rig.queue.current?.ref, rig.player.items[rig.player.currentIndex])
        assertEquals(rig.queue.window(2, 3).map { it.ref }, rig.player.items)
        assertTrue(rig.player.log.none { it.startsWith("setAll") }, rig.player.log.toString())
    }

    @Test
    fun `removing an upcoming song replaces only the tail`() {
        val rig = setUp()
        rig.sync.fill()
        advanceTo(rig, 2)
        rig.player.log.clear()

        rig.queue.remove(rig.queue.index + 1)
        rig.sync.onQueueEdited()

        assertEquals(rig.queue.window(2, 3).map { it.ref }, rig.player.items)
        assertTrue(rig.player.log.none { it.startsWith("setAll") || it.startsWith("prepend") }, rig.player.log.toString())
    }

    @Test
    fun `putting a played song back restores the window without a refill`() {
        val rig = setUp()
        rig.sync.fill()
        advanceTo(rig, 3)
        val entry = rig.queue.remove(1)!!
        rig.sync.onQueueEdited()
        rig.player.log.clear()

        rig.queue.insertAt(1, entry)
        rig.sync.onQueueEdited()

        assertEquals(rig.queue.window(2, 3).map { it.ref }, rig.player.items)
        assertEquals(rig.queue.current?.ref, rig.player.items[rig.player.currentIndex])
        assertTrue(rig.player.log.none { it.startsWith("setAll") }, rig.player.log.toString())
    }

    @Test
    fun `fill after replace holds the window around the current song`() {
        val (queue, player, sync) = setUp()

        sync.fill()

        assertEquals(refs("ABCD"), player.items)
        assertEquals(0, player.currentIndex)
        assertEquals(queue.current?.ref, player.items[player.currentIndex])
    }

    @Test
    fun `never more than behind + ahead + 1 items`() {
        val (queue, player, sync) = setUp()
        sync.fill()

        repeat(15) {
            queue.next()
            player.currentIndex++
            sync.onPlayerAdvanced()
        }

        assertTrue(player.items.size <= 2 + 3 + 1, "${player.items.size} items")
        assertEquals(queue.current?.ref, player.items[player.currentIndex])
    }

    @Test
    fun `after next exactly the missing entries are appended and old ones trimmed`() {
        val (queue, player, sync) = setUp()
        sync.fill()
        player.log.clear()

        repeat(4) {
            queue.next()
            player.currentIndex++
            sync.onPlayerAdvanced()
        }

        assertEquals(refs("CDEFGH"), player.items)
        assertEquals(2, player.currentIndex)
        assertTrue(
            player.log.all { it == "append(1)" || it == "removeFirst(1)" },
            player.log.toString(),
        )
    }

    @Test
    fun `shuffle toggle replaces only the items after the current one`() {
        val (queue, player, sync) = setUp()
        sync.fill()
        queue.next()
        player.currentIndex++
        sync.onPlayerAdvanced()
        player.log.clear()

        queue.setShuffled(true)
        sync.onQueueReordered()

        assertEquals(refs("AB"), player.items.take(2))
        assertEquals(1, player.currentIndex)
        assertEquals(queue.window(0, 3).drop(1).map { it.ref }, player.items.drop(2))
        assertEquals(listOf("removeRange(2, 5)", "append(3)"), player.log)
    }

    @Test
    fun `a new play request replaces everything`() {
        val (queue, player, sync) = setUp()
        sync.fill()

        queue.replace(refs("XYZ"), 1, PlaySource.ALBUM, shuffled = false)
        sync.onQueueReplaced()

        assertEquals(refs("XYZ"), player.items)
        assertEquals(1, player.currentIndex)
    }

    @Test
    fun `repeat all wraps the window past the end`() {
        val (queue, player, sync) = setUp(size = 5)
        queue.repeatMode = RepeatMode.ALL
        queue.jumpTo(4)
        sync.fill()

        assertEquals(refs("CDEAB"), player.items)
        assertEquals(2, player.currentIndex)
    }

    @Test
    fun `an item the player could not open is removed and the window refilled`() {
        val (queue, player, sync) = setUp()
        sync.fill()

        sync.onItemFailed(queue.window(0, 1)[1].ref)

        assertEquals(refs("ACDE"), player.items)
        assertEquals(19, queue.size)
    }
}
