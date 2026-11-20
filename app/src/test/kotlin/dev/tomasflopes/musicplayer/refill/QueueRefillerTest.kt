package dev.tomasflopes.musicplayer.refill

import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.queue.RefillSeed
import dev.tomasflopes.musicplayer.fakes.FakePlaybackController
import dev.tomasflopes.musicplayer.fakes.testSong
import dev.tomasflopes.musicplayer.playback.RefillSignal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueRefillerTest {
    private val seed = RefillSeed.Song(1)
    private val playback = FakePlaybackController()
    private val signals = MutableStateFlow<RefillSignal?>(null)

    private class FakeSource(
        private val kind: RefillSeed,
        override val lowWater: Int = 2,
        var on: Boolean = true,
        var songsToGive: List<Long> = listOf(10, 11),
        private val source: PlaySource = PlaySource.RADIO,
    ) : RefillSource {
        var batchCalls = 0
        var gate: CompletableDeferred<Unit>? = null

        override fun handles(seed: RefillSeed) = seed::class == kind::class

        override suspend fun enabled() = on

        override suspend fun batch(signal: RefillSignal): RefillBatch {
            batchCalls++
            gate?.await()
            return RefillBatch(songsToGive.map { testSong(it) }, source)
        }
    }

    private fun TestScope.refiller(
        vararg sources: RefillSource,
        onResolve: () -> Unit = {},
    ) = QueueRefiller(signals, { onResolve(); sources.toSet() }, playback, backgroundScope).also { it.start() }

    @Test
    fun `it does nothing without a signal and never resolves the sources`() = runTest {
        var resolved = 0
        refiller(FakeSource(seed), onResolve = { resolved++ })

        runCurrent()

        assertTrue(playback.appendedBatches.isEmpty())
        assertEquals(0, resolved)
    }

    @Test
    fun `it appends when the songs ahead are at or below the low water mark`() = runTest {
        refiller(FakeSource(seed))

        signals.value = signal(seed, refs(1), nonManualAhead = 2, epoch = 4)
        runCurrent()

        val sent = playback.appendedBatches.single()
        assertEquals(listOf(10L, 11L), sent.songs.map { it.songId.value })
        assertEquals(seed, sent.seed)
        assertEquals(4, sent.epoch)
        assertEquals(PlaySource.RADIO, sent.source)
    }

    @Test
    fun `it sends the batch's own source`() = runTest {
        val mixSeed = RefillSeed.Mix(3)
        refiller(FakeSource(mixSeed, lowWater = 5, source = PlaySource.MIX))

        signals.value = signal(mixSeed, refs(1), nonManualAhead = 5)
        runCurrent()

        assertEquals(PlaySource.MIX, playback.appendedBatches.single().source)
    }

    @Test
    fun `it does nothing above the low water mark`() = runTest {
        val source = FakeSource(seed)
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 3)
        runCurrent()

        assertTrue(playback.appendedBatches.isEmpty())
        assertEquals(0, source.batchCalls)
    }

    @Test
    fun `it does nothing while the source is disabled and acts again once it is on`() = runTest {
        val source = FakeSource(seed, on = false)
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 2)
        runCurrent()
        assertEquals(0, source.batchCalls)

        source.on = true
        signals.value = signal(seed, refs(1, 2), nonManualAhead = 1)
        runCurrent()
        assertEquals(1, playback.appendedBatches.size)
    }

    @Test
    fun `it picks the source that handles the seed`() = runTest {
        val solo = FakeSource(RefillSeed.Song(0), songsToGive = listOf(10))
        val mix = FakeSource(RefillSeed.Mix(0), lowWater = 5, songsToGive = listOf(20))
        refiller(solo, mix)

        signals.value = signal(RefillSeed.Mix(3), refs(1), nonManualAhead = 4)
        runCurrent()

        assertEquals(listOf(20L), playback.appendedBatches.single().songs.map { it.songId.value })
        assertEquals(0, solo.batchCalls)
    }

    @Test
    fun `an empty batch is not asked for again in the same epoch`() = runTest {
        val source = FakeSource(seed, songsToGive = emptyList())
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 0, epoch = 2)
        runCurrent()
        signals.value = signal(seed, refs(1), nonManualAhead = 1, epoch = 2)
        runCurrent()

        assertEquals(1, source.batchCalls)
        assertTrue(playback.appendedBatches.isEmpty())
    }

    @Test
    fun `an empty batch is asked for again after the epoch moves`() = runTest {
        val source = FakeSource(seed, songsToGive = emptyList())
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 0, epoch = 2)
        runCurrent()
        signals.value = signal(seed, refs(1), nonManualAhead = 0, epoch = 3)
        runCurrent()

        assertEquals(2, source.batchCalls)
    }

    @Test
    fun `a newer signal cancels a batch still being built`() = runTest {
        val source = FakeSource(seed)
        source.gate = CompletableDeferred()
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 1, epoch = 1)
        runCurrent()
        val first = source.gate
        source.gate = null
        signals.value = signal(seed, refs(2), nonManualAhead = 1, epoch = 2)
        runCurrent()
        first?.complete(Unit)
        runCurrent()

        assertEquals(listOf(2), playback.appendedBatches.map { it.epoch })
    }

    @Test
    fun `a signal that goes null cancels the build and sends nothing`() = runTest {
        val source = FakeSource(seed)
        source.gate = CompletableDeferred()
        refiller(source)

        signals.value = signal(seed, refs(1), nonManualAhead = 1)
        runCurrent()
        signals.value = null
        runCurrent()
        source.gate?.complete(Unit)
        runCurrent()

        assertTrue(playback.appendedBatches.isEmpty())
    }
}
