package dev.tomasflopes.musicplayer.data.scan

import dev.tomasflopes.musicplayer.core.model.SongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScanPlannerTest {
    private val volume = "external_primary"

    private fun file(
        path: String,
        mediaStoreId: Long = path.hashCode().toLong(),
        size: Long = 100,
        modified: Long = 10,
    ) = MediaFile(
        mediaStoreId = mediaStoreId,
        volumeName = volume,
        path = path,
        sizeBytes = size,
        modifiedEpochSec = modified,
        dateAddedEpochSec = 1,
        durationMs = 1_000,
        contentUri = "content://$mediaStoreId",
    )

    private fun known(
        id: Long,
        path: String,
        mediaStoreId: Long = path.hashCode().toLong(),
        size: Long = 100,
        modified: Long = 10,
        inLibrary: Boolean = true,
    ) = KnownSong(SongId(id), mediaStoreId, volume, path, size, modified, inLibrary)

    private val empty =
        ScanPlan(
            insert = emptyList(),
            rereadTags = emptyList(),
            move = emptyList(),
            revive = emptyList(),
            refreshMediaStoreId = emptyList(),
            retire = emptyList(),
            unchanged = 0,
        )

    @Test
    fun `new file is inserted`() {
        val plan = ScanPlanner.plan(listOf(file("Music/a.mp3")), emptyList())

        assertEquals(empty.copy(insert = listOf(file("Music/a.mp3"))), plan)
    }

    @Test
    fun `same path, size and mtime is unchanged and reads no tags`() {
        val plan = ScanPlanner.plan(listOf(file("Music/a.mp3")), listOf(known(1, "Music/a.mp3")))

        assertEquals(empty.copy(unchanged = 1), plan)
    }

    @Test
    fun `changed size or mtime re-reads tags under the same id`() {
        val bigger = file("Music/a.mp3", size = 200)
        val newer = file("Music/b.mp3", modified = 11)

        val plan = ScanPlanner.plan(listOf(bigger, newer), listOf(known(1, "Music/a.mp3"), known(2, "Music/b.mp3")))

        assertEquals(empty.copy(rereadTags = listOf(SongId(1) to bigger, SongId(2) to newer)), plan)
    }

    @Test
    fun `path gone but same MediaStore id at a new path is a move with the same id`() {
        val moved = file("Music/Rap/a.mp3", mediaStoreId = 7)

        val plan = ScanPlanner.plan(listOf(moved), listOf(known(1, "Music/a.mp3", mediaStoreId = 7)))

        assertEquals(empty.copy(move = listOf(SongId(1) to moved)), plan)
    }

    @Test
    fun `same path with a new MediaStore id is unchanged and the id is refreshed`() {
        val reindexed = file("Music/a.mp3", mediaStoreId = 99)

        val plan = ScanPlanner.plan(listOf(reindexed), listOf(known(1, "Music/a.mp3", mediaStoreId = 7)))

        assertEquals(empty.copy(refreshMediaStoreId = listOf(SongId(1) to reindexed), unchanged = 1), plan)
    }

    @Test
    fun `missing file is retired, never deleted`() {
        val plan = ScanPlanner.plan(emptyList(), listOf(known(1, "Music/a.mp3")))

        assertEquals(empty.copy(retire = listOf(SongId(1))), plan)
    }

    @Test
    fun `already retired file stays out of the plan`() {
        val plan = ScanPlanner.plan(emptyList(), listOf(known(1, "Music/a.mp3", inLibrary = false)))

        assertEquals(empty, plan)
    }

    @Test
    fun `retired file found again is revived with the same id`() {
        val back = file("Music/a.mp3")

        val plan = ScanPlanner.plan(listOf(back), listOf(known(1, "Music/a.mp3", inLibrary = false)))

        assertEquals(empty.copy(revive = listOf(SongId(1) to back)), plan)
    }

    @Test
    fun `revived file whose content changed is revived and re-read`() {
        val changed = file("Music/a.mp3", size = 300)

        val plan = ScanPlanner.plan(listOf(changed), listOf(known(1, "Music/a.mp3", inLibrary = false)))

        assertEquals(empty.copy(revive = listOf(SongId(1) to changed), rereadTags = listOf(SongId(1) to changed)), plan)
    }

    @Test
    fun `moved file that also changed is moved and re-read`() {
        val moved = file("Music/Rap/a.mp3", mediaStoreId = 7, modified = 99)

        val plan = ScanPlanner.plan(listOf(moved), listOf(known(1, "Music/a.mp3", mediaStoreId = 7)))

        assertEquals(empty.copy(move = listOf(SongId(1) to moved), rereadTags = listOf(SongId(1) to moved)), plan)
    }

    @Test
    fun `root changed to a sibling and back revives every original id`() {
        val original = listOf(file("Music/a.mp3"), file("Music/b.mp3"))
        val knownAfterFirstScan = original.mapIndexed { i, f -> known(i + 1L, f.path, f.mediaStoreId) }

        val sibling = ScanPlanner.plan(listOf(file("Podcasts/x.mp3")), knownAfterFirstScan)
        assertEquals(listOf(SongId(1), SongId(2)), sibling.retire)
        assertEquals(1, sibling.insert.size)

        val retired = knownAfterFirstScan.map { it.copy(inLibrary = false) } + known(3, "Podcasts/x.mp3")
        val back = ScanPlanner.plan(original, retired)

        assertEquals(listOf(SongId(1), SongId(2)), back.revive.map { it.first })
        assertEquals(listOf(SongId(3)), back.retire)
        assertEquals(emptyList<MediaFile>(), back.insert)
    }

    @Test
    fun `paths are matched per volume`() {
        val onSdCard = file("Music/a.mp3").copy(volumeName = "1a2b-3c4d", mediaStoreId = 500)

        val plan = ScanPlanner.plan(listOf(onSdCard), listOf(known(1, "Music/a.mp3")))

        assertEquals(listOf(onSdCard), plan.insert)
        assertEquals(listOf(SongId(1)), plan.retire)
    }

    @Test
    fun `two files claiming the same MediaStore id do not both move onto one song`() {
        val a = file("Music/Rap/a.mp3", mediaStoreId = 7)
        val b = file("Music/Rap/b.mp3", mediaStoreId = 7)

        val plan = ScanPlanner.plan(listOf(a, b), listOf(known(1, "Music/a.mp3", mediaStoreId = 7)))

        assertEquals(listOf(SongId(1) to a), plan.move)
        assertEquals(listOf(b), plan.insert)
    }
}
