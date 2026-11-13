package dev.tomasflopes.musicplayer.core.tags

import dev.tomasflopes.musicplayer.core.tags.TextEncoding.UTF16
import dev.tomasflopes.musicplayer.core.tags.TextEncoding.UTF8
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

class Id3RobustnessTest {
    private val reader = Id3TagReader()
    private val audio = SilentMp3.frames(1)

    private val wellFormedFiles =
        listOf(
            id3v22 {
                text("TT2", "Song")
                text("TRK", "3/9")
                picture(ByteArray(300) { it.toByte() })
                audio(audio)
            },
            id3v23 {
                extendedHeader()
                text("TIT2", "HUMBLE.", UTF16)
                text("TPE1", "Kendrick Lamar", UTF16)
                picture(ByteArray(2_000) { it.toByte() })
                text("TRCK", "8/14")
                padding(40)
                audio(audio)
            },
            id3v23 {
                unsynchronised()
                text("TIT2", "ÿes", UTF16)
                picture(ByteArray(500) { -1 })
                audio(audio)
            },
            id3v24 {
                text("TIT2", "HUMBLE".repeat(50), UTF8)
                text("TPE1", "Ed\u0000SZA", UTF8)
                picture(ByteArray(2_000) { it.toByte() })
                audio(audio)
            },
            id3v24 {
                plainFrameSizes()
                unsynchronised()
                text("TIT2", "HUMBLE".repeat(50), UTF8)
                audio(audio)
            },
            audio + id3v1(title = "Song", artist = "Artist", album = "Album", track = 5),
        )

    @Test
    fun `1000 random truncations and byte flips never throw`() {
        val random = Random(37)

        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            repeat(1_000) { round ->
                val damaged = damage(wellFormedFiles[round % wellFormedFiles.size], random)

                val result = reader.read(ByteArrayTagSource(damaged))

                // The reader's last-resort catch reports "internal error"; damaged input must be handled on purpose.
                val crashed = result is TagReadResult.Failure && result.reason.startsWith("internal error")
                assertFalse(crashed, "round $round: $result")
            }
        }
    }

    private fun damage(
        file: ByteArray,
        random: Random,
    ): ByteArray {
        val damaged = if (random.nextBoolean()) file.copyOf(random.nextInt(file.size)) else file.copyOf()
        if (damaged.isNotEmpty()) {
            repeat(random.nextInt(1, 9)) {
                val index = random.nextInt(minOf(damaged.size, 400))
                damaged[index] = random.nextInt().toByte()
            }
        }
        return damaged
    }
}
