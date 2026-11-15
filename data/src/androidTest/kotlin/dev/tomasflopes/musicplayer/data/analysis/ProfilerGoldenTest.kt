package dev.tomasflopes.musicplayer.data.analysis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.analyse
import dev.tomasflopes.musicplayer.core.analysis.contract.GoldenCompare
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The profiler on MediaCodec PCM against the golden profiles made on the Mac with ffmpeg (ADR 0060, 0065), in
 * `deviceCheck`. It lists every file that differs at once.
 */
@RunWith(AndroidJUnit4::class)
class ProfilerGoldenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun mediaCodecProfilesMatchTheMacGolden() {
        val golden =
            ProfileExportCodec.read(
                instrumentation.context.assets
                    .open("golden-profiles.jsonl")
                    .bufferedReader()
                    .use { it.readText() }
                    .lines(),
            )
        val source = MediaCodecPcmSource(instrumentation.targetContext)

        val failures =
            golden.records.flatMap { record ->
                val target = File(instrumentation.targetContext.cacheDir, record.path)
                instrumentation.context.assets.open(record.path).use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                val profile = HandBuiltProfilerV1().analyse(source.decodeAll(target.absolutePath))
                GoldenCompare.differences(record.profile, profile).map { "${record.path}: $it" }
            }

        assertTrue("Failed checks:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
