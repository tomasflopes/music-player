package dev.tomasflopes.musicplayer.playback.bass

import androidx.media3.common.util.UnstableApi
import dev.tomasflopes.musicplayer.core.model.transition.TransitionConfig
import org.junit.jupiter.api.Test

/** [ShelfAudioProcessor] against the [BassControl] contract (ADR 0102); the audiofx one runs it on a device. */
@OptIn(UnstableApi::class)
class ShelfAudioProcessorContractTest {
    @Test
    fun `the shelf processor keeps the bass control contract`() {
        val failures =
            BassControlContract
                .checks(TransitionConfig().bassCutDb) { ShelfAudioProcessor(TransitionConfig()) }
                .mapNotNull { runCatching(it.run).exceptionOrNull()?.let { e -> "${it.name}: $e" } }

        check(failures.isEmpty()) { failures.joinToString("; ") }
    }
}
