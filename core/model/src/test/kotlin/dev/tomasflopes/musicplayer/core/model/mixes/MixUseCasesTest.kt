package dev.tomasflopes.musicplayer.core.model.mixes

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MixUseCasesTest {
    private class FakeStore : MixStore {
        val created = mutableListOf<Triple<String, List<MixSeed>, Long>>()
        val renamed = mutableListOf<String>()
        val replaced = mutableListOf<List<MixSeed>>()

        override suspend fun create(
            name: String,
            seeds: List<MixSeed>,
            nowEpochMs: Long,
        ): MixId {
            created += Triple(name, seeds, nowEpochMs)
            return MixId(created.size.toLong())
        }

        override suspend fun rename(
            id: MixId,
            name: String,
            nowEpochMs: Long,
        ) {
            renamed += name
        }

        override suspend fun replaceSeeds(
            id: MixId,
            seeds: List<MixSeed>,
            nowEpochMs: Long,
        ) {
            replaced += seeds
        }

        override suspend fun delete(id: MixId) = Unit
    }

    private val clock = Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC)
    private val store = FakeStore()

    private fun songs(count: Int) = (1..count).map { MixSeed(MixSeedKind.SONG, it.toLong()) }

    @Test
    fun `create asks for three to five seeds`() =
        runBlocking {
            val create = CreateMix(store, clock)

            assertEquals(CreateMix.Result.InvalidSeeds, create("Gym", songs(2)))
            assertEquals(CreateMix.Result.InvalidSeeds, create("Gym", songs(6)))
            assertTrue(create("Gym", songs(3)) is CreateMix.Result.Created)
            assertTrue(create("Gym", songs(5)) is CreateMix.Result.Created)
        }

    @Test
    fun `the domain accepts one to five seeds when the caller asks for fewer`() =
        runBlocking {
            val create = CreateMix(store, clock)

            assertTrue(create("Gym", songs(1), minSeeds = 1) is CreateMix.Result.Created)
            assertEquals(CreateMix.Result.InvalidSeeds, create("Gym", emptyList(), minSeeds = 1))
        }

    @Test
    fun `a repeated seed counts once`() =
        runBlocking {
            val seeds = songs(3) + songs(3)

            CreateMix(store, clock)("Gym", seeds)

            assertEquals(songs(3), store.created.single().second)
        }

    @Test
    fun `an empty name creates nothing`() =
        runBlocking {
            assertEquals(CreateMix.Result.InvalidName, CreateMix(store, clock)("   ", songs(3)))
            assertTrue(store.created.isEmpty())
        }

    @Test
    fun `the name is trimmed and the clock gives the time`() =
        runBlocking {
            CreateMix(store, clock)("  Gym  ", songs(3))

            assertEquals("Gym", store.created.single().first)
            assertEquals(500L, store.created.single().third)
        }

    @Test
    fun `update changes nothing unless both name and seeds are valid`() =
        runBlocking {
            val update = UpdateMix(store, clock)

            assertEquals(UpdateMix.Result.INVALID_SEEDS, update(MixId(1), "Gym", emptyList()))
            assertEquals(UpdateMix.Result.INVALID_NAME, update(MixId(1), " ", songs(2)))
            assertTrue(store.renamed.isEmpty() && store.replaced.isEmpty())
            assertEquals(UpdateMix.Result.UPDATED, update(MixId(1), "Gym", songs(1)))
            assertEquals(listOf(songs(1)), store.replaced)
        }
}
