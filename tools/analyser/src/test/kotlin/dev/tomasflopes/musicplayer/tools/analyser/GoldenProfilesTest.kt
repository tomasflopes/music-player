package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.analyse
import dev.tomasflopes.musicplayer.core.analysis.contract.GoldenCompare
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Paths

/**
 * The golden profiles of the generated test MP3s (`testdata/analysis/golden-profiles.jsonl`, made with
 * `scripts/analyse testdata/analysis testdata/analysis/golden-profiles.jsonl`), checked on every `check` run (ADR 0060,
 * 0065). The Android twin is `ProfilerGoldenTest` in `deviceCheck`.
 */
class GoldenProfilesTest {
    private val golden =
        ProfileExportCodec.read(
            checkNotNull(javaClass.classLoader.getResource("golden-profiles.jsonl")) { "Missing golden-profiles.jsonl" }
                .readText()
                .lines(),
        )

    @TestFactory
    fun `every test file profiles like its golden line`(): List<DynamicTest> =
        golden.records.map { record ->
            DynamicTest.dynamicTest(record.path) {
                val url = checkNotNull(javaClass.classLoader.getResource(record.path)) { "Missing ${record.path}" }
                val pcm = FfmpegPcmSource().decodeAll(Paths.get(url.toURI()).toString())

                val differences = GoldenCompare.differences(record.profile, HandBuiltProfilerV1().analyse(pcm))

                assertTrue(differences.isEmpty(), "${record.path}:\n${differences.joinToString("\n")}")
            }
        }

    @Test
    fun `the golden file is current and covers every test file`() {
        assertEquals(HandBuiltProfilerV1.VERSION, golden.header.profilerVersion)
        assertEquals(13, golden.records.size)
        assertEquals(false, golden.truncatedTail)
    }
}
