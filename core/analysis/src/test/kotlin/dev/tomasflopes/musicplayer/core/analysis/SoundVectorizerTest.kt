package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.config.SoundVectorizerConfig
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SoundVectorizerTest {
    private val vectorizer = SoundVectorizer()

    private fun raw(
        id: Long,
        centroid: Float = 1000f,
        bass: Float = 0.3f,
        onset: Float = 2f,
        beat: Float = 0.5f,
        loudnessVariation: Float = 0.05f,
        meanLoudness: Float = 0.1f,
        tempo: Float = 120f,
        zeroCrossings: Float = 0.1f,
        chromaShift: Float = 0f,
        chroma: FloatArray? = null,
    ): RawSoundProfile {
        val fixed = FloatArray(SoundProfile.FIXED_SIZE)
        fixed[SoundProfile.TEMPO] = tempo
        fixed[SoundProfile.MEAN_LOUDNESS] = meanLoudness
        fixed[SoundProfile.LOUDNESS_VARIATION] = loudnessVariation
        fixed[SoundProfile.SPECTRAL_CENTROID] = centroid
        fixed[SoundProfile.BASS_RATIO] = bass
        fixed[SoundProfile.ONSET_DENSITY] = onset
        fixed[SoundProfile.BEAT_STRENGTH] = beat
        fixed[SoundProfile.ZERO_CROSSING_RATE] = zeroCrossings
        for (bin in 0 until SoundProfile.CHROMA_BINS) {
            fixed[SoundProfile.CHROMA_FIRST + bin] = chroma?.get(bin) ?: (chromaShift + bin)
        }
        return RawSoundProfile(id, fixed)
    }

    /** Songs 0..20 whose feature is the song number, so p1 = 0.2 and p99 = 19.8 (numpy's linear rule). */
    private fun ramp(set: (Int, SoundProfileBuilder) -> Unit): List<RawSoundProfile> =
        (0..20).map { buildRamp(it, set) }

    private class SoundProfileBuilder(
        var centroid: Float = 1000f,
        var bass: Float = 0.3f,
        var onset: Float = 2f,
        var beat: Float = 0.5f,
        var loudnessVariation: Float = 0.05f,
        var meanLoudness: Float = 0.1f,
        var tempo: Float = 120f,
        var chroma: FloatArray? = null,
    )

    private fun buildRamp(
        i: Int,
        set: (Int, SoundProfileBuilder) -> Unit,
    ): RawSoundProfile {
        val b = SoundProfileBuilder()
        set(i, b)
        return raw(
            id = (i + 1).toLong(),
            centroid = b.centroid,
            bass = b.bass,
            onset = b.onset,
            beat = b.beat,
            loudnessVariation = b.loudnessVariation,
            meanLoudness = b.meanLoudness,
            tempo = b.tempo,
            chroma = b.chroma,
        )
    }

    private fun SoundSpace.value(
        songId: Long,
        dimension: Int,
    ): Float = values[indexOf(songId) * dim + dimension]

    @Test
    fun `the space has seven dimensions and rows in ascending song id`() {
        val space = vectorizer.vectorize(listOf(raw(30), raw(10), raw(20)))

        assertEquals(7, space.dim)
        assertEquals(SoundVectorizer.DIM, space.dim)
        assertEquals(7, SoundVectorizer.NAMES.size)
        assertEquals(listOf("flatness", "tempo"), SoundVectorizer.NAMES.takeLast(2))
        assertArrayEquals(longArrayOf(10, 20, 30), space.songIds)
    }

    @Test
    fun `an empty library gives an empty space`() {
        val space = vectorizer.vectorize(emptyList())

        assertEquals(0, space.size)
        assertEquals(7, space.dim)
    }

    @Test
    fun `values are clipped at p1 and p99 and scaled to 0 to 1`() {
        // Centroid 0..20 over 21 songs: p1 is 0.2, p99 is 19.8.
        val space = vectorizer.vectorize(ramp { i, b -> b.centroid = i.toFloat() })

        assertEquals(0f, space.value(1, 0), 1e-6f) // centroid 0, below p1
        assertEquals(0.8f / 19.6f, space.value(2, 0), 1e-6f) // centroid 1, just above p1
        assertEquals(0.5f, space.value(11, 0), 1e-6f) // centroid 10
        assertEquals(18.8f / 19.6f, space.value(20, 0), 1e-6f) // centroid 19, just below p99
        assertEquals(1f, space.value(21, 0), 1e-6f) // centroid 20, above p99
    }

    @Test
    fun `percentiles interpolate linearly like numpy`() {
        // Four values 0, 10, 20, 30: p1 sits at index 0.03 -> 0.3, p99 at index 2.97 -> 29.7.
        val profiles = listOf(0f, 10f, 20f, 30f).mapIndexed { i, c -> raw((i + 1).toLong(), centroid = c) }

        val space = vectorizer.vectorize(profiles)

        assertEquals((10f - 0.3f) / 29.4f, space.value(2, 0), 1e-6f)
        assertEquals((20f - 0.3f) / 29.4f, space.value(3, 0), 1e-6f)
    }

    @Test
    fun `weights are applied inside the vectorizer`() {
        val space =
            vectorizer.vectorize(
                ramp { i, b ->
                    b.centroid = i.toFloat()
                    b.bass = i.toFloat()
                    b.onset = i.toFloat()
                    b.beat = i.toFloat()
                    b.loudnessVariation = i * 0.1f
                    b.meanLoudness = 0.5f
                    b.chroma = spikedChroma(spike = (20 - i) * 5f) // flatter with every song, uniform at the last
                    b.tempo = Math.pow(2.0, i.toDouble()).toFloat()
                },
            )

        // The last song is above p99 in every feature, so each value is its weight.
        val top = space.vector(space.indexOf(21))
        assertArrayEquals(floatArrayOf(1.0f, 1.0f, 0.5f, 0.8f, 0.8f, 0.6f, 0.4f), top, 1e-6f)
    }

    /** Eleven bins of 1 and bin 0 of 1 + [spike]: spike 0 is uniform, a large spike is nearly one bin. */
    private fun spikedChroma(spike: Float) = FloatArray(SoundProfile.CHROMA_BINS) { if (it == 0) 1f + spike else 1f }

    @Test
    fun `beat strength takes its square root after it is normalised`() {
        // Beat strength 0..20 over 21 songs: song 11 (value 10) normalises to 0.5, then sqrt, then weight 0.8.
        val space = vectorizer.vectorize(ramp { i, b -> b.beat = i.toFloat() })

        assertEquals(Math.sqrt(0.5).toFloat() * 0.8f, space.value(11, 3), 1e-6f)
    }

    @Test
    fun `relative dynamics is loudness variation over mean loudness`() {
        // Mean 0.5 and variation 0..2 in steps of 0.1 give ratios 0..4; song 11 has ratio 2 -> 0.5 -> weight 0.8.
        val space =
            vectorizer.vectorize(
                ramp { i, b ->
                    b.meanLoudness = 0.5f
                    b.loudnessVariation = i * 0.1f
                },
            )

        assertEquals(0.5f * 0.8f, space.value(11, 4), 1e-5f)
    }

    @Test
    fun `zero crossings, the loudness level and the music end do not change the vector`() {
        val base =
            ramp { i, b ->
                b.centroid = i.toFloat()
                b.loudnessVariation = i * 0.1f
                b.meanLoudness = 0.5f
            }
        val changed =
            base.map {
                val fixed = it.fixed.copyOf()
                fixed[SoundProfile.ZERO_CROSSING_RATE] = 0.9f / it.songId
                fixed[SoundProfile.MUSIC_END] = 999f
                // Twice as loud with twice the variation is the same relative dynamics.
                fixed[SoundProfile.MEAN_LOUDNESS] *= 2
                fixed[SoundProfile.LOUDNESS_VARIATION] *= 2
                RawSoundProfile(it.songId, fixed)
            }

        assertArrayEquals(vectorizer.vectorize(base).values, vectorizer.vectorize(changed).values, 1e-6f)
    }

    @Test
    fun `tempo is the log2 of the stored tempo, scaled like the others`() {
        // Tempos 1, 2, 4, ... 2^20 give log2 0..20; song 11 (2^10) is 10, so (10 - 0.2) / 19.6 = 0.5, weight 0.4.
        val space = vectorizer.vectorize(ramp { i, b -> b.tempo = Math.pow(2.0, i.toDouble()).toFloat() })

        assertEquals(0.5f * 0.4f, space.value(11, 6), 1e-6f)
    }

    @Test
    fun `a tempo below 1 counts as 1`() {
        val space =
            vectorizer.vectorize(
                listOf(raw(1, tempo = 0f), raw(2, tempo = 1f), raw(3, tempo = -5f), raw(4, tempo = 8f)),
            )

        assertEquals(space.value(2, 6), space.value(1, 6), 0f)
        assertEquals(space.value(2, 6), space.value(3, 6), 0f)
        assertTrue(space.value(4, 6) > space.value(2, 6))
    }

    @Test
    fun `tonal flatness is 1 for uniform chroma, 0 for a single bin and 0 for zero chroma`() {
        fun flatness(chroma: FloatArray) = SoundVectorizer.tonalFlatness(raw(1, chroma = chroma).fixed)

        assertEquals(1.0, flatness(FloatArray(SoundProfile.CHROMA_BINS) { 0.7f }), 1e-9)
        assertEquals(0.0, flatness(FloatArray(SoundProfile.CHROMA_BINS) { if (it == 4) 3f else 0f }), 1e-9)
        assertEquals(0.0, flatness(FloatArray(SoundProfile.CHROMA_BINS)), 0.0)
    }

    @Test
    fun `tonal flatness is the entropy of the normalised chroma over ln 12`() {
        // Two equal bins: entropy ln 2, so flatness ln 2 / ln 12.
        val chroma = FloatArray(SoundProfile.CHROMA_BINS) { if (it < 2) 5f else 0f }

        val flatness = SoundVectorizer.tonalFlatness(raw(1, chroma = chroma).fixed)

        assertEquals(Math.log(2.0) / Math.log(12.0), flatness, 1e-9)
    }

    @Test
    fun `a feature that is the same for every song becomes 0 and not NaN`() {
        val space = vectorizer.vectorize(listOf(raw(1), raw(2), raw(3)))

        assertTrue(space.values.all { it == 0f })
    }

    @Test
    fun `a song with a non finite feature is left out`() {
        val space =
            vectorizer.vectorize(
                listOf(raw(1), raw(2, centroid = Float.NaN), raw(3, bass = Float.POSITIVE_INFINITY)),
            )

        assertArrayEquals(longArrayOf(1), space.songIds)
    }

    @Test
    fun `a silent song with zero mean loudness has relative dynamics 0`() {
        val space = vectorizer.vectorize(listOf(raw(1, meanLoudness = 0f, loudnessVariation = 0f), raw(2), raw(3)))

        assertTrue(space.values.all { it.isFinite() })
        assertEquals(0f, space.value(1, 4), 0f)
    }

    @Test
    fun `the vectorizer is deterministic`() {
        val profiles = ramp { i, b -> b.centroid = (i * 7 % 13).toFloat() }

        assertArrayEquals(vectorizer.vectorize(profiles).values, vectorizer.vectorize(profiles.reversed()).values, 0f)
    }

    @Test
    fun `the config rejects numbers that cannot work`() {
        assertThrows(IllegalArgumentException::class.java) {
            SoundVectorizerConfig(lowPercentile = 99.0, highPercentile = 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(lowPercentile = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(highPercentile = 101.0) }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(onsetWeight = -0.1) }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(bassWeight = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(tempoWeight = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { SoundVectorizerConfig(flatnessWeight = Double.NaN) }
    }

    @Test
    fun `the default config is p1 to p99 with the ADR 0087 weights`() {
        val config = SoundVectorizerConfig()

        assertEquals(1.0, config.lowPercentile, 0.0)
        assertEquals(99.0, config.highPercentile, 0.0)
        assertEquals(listOf(1.0, 1.0, 0.5, 0.8, 0.8, 0.6, 0.4), config.weights())
    }
}
