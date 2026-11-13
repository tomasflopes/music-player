package dev.tomasflopes.musicplayer.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The listening log is the source of truth (design §7.3): nothing may change or remove a row, or drop the database. */
class LogIsAppendOnlyTest {
    private val mainSources =
        File("src/main").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()

    private fun sourcesOf(vararg names: String) = mainSources.filter { it.name in names }

    private fun assertNoMatch(
        regex: Regex,
        files: List<File>,
    ) {
        val hits = files.filter { regex.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList<String>(), hits, "forbidden ${regex.pattern}")
    }

    @Test
    fun `the scan sees the main sources and both log DAOs`() {
        assertTrue(mainSources.isNotEmpty())
        assertEquals(2, sourcesOf("PlayEventDao.kt", "SearchEventDao.kt").size)
    }

    @Test
    fun `log tables are insert-only and migrations are never destructive`() {
        assertNoMatch(Regex("fallbackToDestructiveMigration"), mainSources)
        assertNoMatch(Regex("@(Update|Delete|Upsert)"), sourcesOf("PlayEventDao.kt", "SearchEventDao.kt"))
        assertNoMatch(
            Regex(
                "(DELETE\\s+FROM|UPDATE|REPLACE\\s+INTO|DROP\\s+TABLE)\\s+`?(play_event|search_event)",
                RegexOption.IGNORE_CASE,
            ),
            mainSources,
        )
    }
}
