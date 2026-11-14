package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.contract.ContractFixtures
import dev.tomasflopes.musicplayer.core.analysis.contract.PcmSourceContract
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Paths

/** The `PcmSource` contract against ffmpeg, in `check` (ADR 0053). The MediaCodec twin runs in `deviceCheck`. */
class FfmpegPcmSourceContractTest {
    private class ResourceFixtures : ContractFixtures {
        override fun location(name: String): String {
            val url = checkNotNull(javaClass.classLoader.getResource("$name.mp3")) { "Missing fixture $name.mp3" }
            return Paths.get(url.toURI()).toString()
        }

        override fun missingLocation(): String = "/nonexistent/analyser/missing.mp3"

        override fun referenceText(): String =
            checkNotNull(javaClass.classLoader.getResource("reference.tsv")) { "Missing reference.tsv" }.readText()
    }

    @TestFactory
    fun `ffmpeg meets the pcm source contract`(): List<DynamicTest> =
        PcmSourceContract
            .checks(FfmpegPcmSource(), ResourceFixtures())
            .map { check -> DynamicTest.dynamicTest(check.name) { check.run() } }
}
