package dev.tomasflopes.musicplayer.data.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.analysis.contract.ContractFixtures
import dev.tomasflopes.musicplayer.core.analysis.contract.PcmSourceContract
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The `PcmSource` contract against MediaCodec, in `deviceCheck` (ADR 0009, 0053). It runs the same checks as the ffmpeg
 * test on the same generated MP3s (`testdata/analysis`, packed as test assets) and lists every failing check at once.
 */
@RunWith(AndroidJUnit4::class)
class MediaCodecPcmSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val fixtures =
        object : ContractFixtures {
            override fun location(name: String): String {
                val target = File(instrumentation.targetContext.cacheDir, "$name.mp3")
                instrumentation.context.assets.open("$name.mp3").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                return target.absolutePath
            }

            override fun missingLocation(): String =
                File(instrumentation.targetContext.cacheDir, "missing.mp3").absolutePath

            override fun referenceText(): String =
                instrumentation.context.assets
                    .open("reference.tsv")
                    .bufferedReader()
                    .use { it.readText() }
        }

    @Test
    fun mediaCodecMeetsThePcmSourceContract() {
        val source = MediaCodecPcmSource(instrumentation.targetContext)

        val failures =
            PcmSourceContract.checks(source, fixtures).mapNotNull { check ->
                runCatching { check.run() }.exceptionOrNull()?.let { "${check.name}: ${it.message}" }
            }

        assertTrue("Failed checks:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
