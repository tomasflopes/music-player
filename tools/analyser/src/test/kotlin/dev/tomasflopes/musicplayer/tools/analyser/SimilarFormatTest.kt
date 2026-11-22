package dev.tomasflopes.musicplayer.tools.analyser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimilarFormatTest {
    @Test
    fun `the seed, the column names and one line per neighbour with distance and the seven values`() {
        val text =
            SimilarFormat.format(
                seedPath = "HH/A/a.mp3",
                seedValues = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f),
                rows =
                    listOf(
                        SimilarRow("HH/B/b.mp3", 0.2504, floatArrayOf(0f, 1f, 0.5f, 0.25f, 0.126f, 0f, 1f)),
                        SimilarRow("HH/C/c.mp3", 1.5, floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f)),
                    ),
            )

        val expected =
            """
            seed: HH/A/a.mp3
            columns: centroid bass onset beat dynamics flatness tempo (normalised, weighted)
             0  d=0.000  0.10 0.20 0.30 0.40 0.50 0.60 0.70  HH/A/a.mp3
             1  d=0.250  0.00 1.00 0.50 0.25 0.13 0.00 1.00  HH/B/b.mp3
             2  d=1.500  1.00 1.00 1.00 1.00 1.00 1.00 1.00  HH/C/c.mp3
            """.trimIndent() + "\n"
        assertEquals(expected, text)
    }

    @Test
    fun `no neighbours still prints the seed and says so`() {
        val text = SimilarFormat.format("a.mp3", FloatArray(7), emptyList())

        assertEquals(true, text.endsWith("no other songs\n"))
    }
}
