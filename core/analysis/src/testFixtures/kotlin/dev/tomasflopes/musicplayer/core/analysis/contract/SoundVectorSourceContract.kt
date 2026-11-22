package dev.tomasflopes.musicplayer.core.analysis.contract

import dev.tomasflopes.musicplayer.core.analysis.SoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundVectorizer
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import kotlinx.coroutines.runBlocking

/**
 * A song as the contract seeds it: the feature values it varies (tempo and chroma stay 0), and the three ways a
 * profile can be out of date.
 */
class SoundFixtureSong(
    val key: String,
    val centroid: Float,
    val bass: Float,
    val onset: Float,
    val beat: Float,
    val loudnessVariation: Float,
    val meanLoudness: Float = 0.1f,
    val inLibrary: Boolean = true,
    val currentVersion: Boolean = true,
    val fileUnchanged: Boolean = true,
) {
    /** The 33 stored floats; every value the vectorizer does not read stays 0. */
    fun fixed(): FloatArray =
        FloatArray(SoundProfile.FIXED_SIZE).also {
            it[SoundProfile.MEAN_LOUDNESS] = meanLoudness
            it[SoundProfile.LOUDNESS_VARIATION] = loudnessVariation
            it[SoundProfile.SPECTRAL_CENTROID] = centroid
            it[SoundProfile.BASS_RATIO] = bass
            it[SoundProfile.ONSET_DENSITY] = onset
            it[SoundProfile.BEAT_STRENGTH] = beat
        }
}

/** A source seeded with the songs of one scenario, and the id each song got (a database picks its own). */
class SeededSoundSource(
    val source: SoundVectorSource,
    val idOf: (String) -> Long,
)

/** Builds a fresh [SoundVectorSource] holding exactly these songs, each with its profile. */
fun interface SoundSourceFactory {
    fun create(songs: List<SoundFixtureSong>): SeededSoundSource
}

/**
 * What every [SoundVectorSource] must do (ADR 0069): list only in-library songs with a profile of the current
 * version made from the current file, normalise over those songs alone, keep rows in ascending song id, and build
 * the space once. Plain code with no test framework, like [PcmSourceContract], so JUnit 5 (`check`, a fake) and
 * JUnit 4 (`deviceCheck`, Room) run the same checks.
 */
object SoundVectorSourceContract {
    private val library =
        listOf(
            SoundFixtureSong("a", 500f, 0.10f, 1.0f, 0.20f, 0.02f),
            SoundFixtureSong("b", 1500f, 0.30f, 2.0f, 0.40f, 0.04f),
            SoundFixtureSong("c", 2500f, 0.50f, 3.0f, 0.60f, 0.06f),
            SoundFixtureSong("d", 3500f, 0.70f, 4.0f, 0.80f, 0.08f),
        )
    private val extreme = SoundFixtureSong("far", 90_000f, 0.99f, 90f, 1.0f, 9f)

    /** Three songs far from the library, each out of date in one way. */
    private val outOfDate =
        listOf(extreme.copyOf("retired", inLibrary = false), extreme.copyOf("old", currentVersion = false)) +
            extreme.copyOf("replaced", fileUnchanged = false)

    fun checks(factory: SoundSourceFactory): List<ContractCheck> =
        listOf(
            ContractCheck("lists the in-library songs that have a current profile") { listsLibrary(factory) },
            ContractCheck("rows come in ascending song id") { ascending(factory) },
            ContractCheck("values are finite and between 0 and 1") { valuesInRange(factory) },
            ContractCheck("a retired song is left out") { leftOut(factory, outOfDate[0]) },
            ContractCheck("a profile of another profiler version is left out") { leftOut(factory, outOfDate[1]) },
            ContractCheck("a profile of a replaced file is left out") { leftOut(factory, outOfDate[2]) },
            ContractCheck("songs left out do not move the others") { leftOutDoNotNormalise(factory) },
            ContractCheck("no profiled song gives an empty space") { emptySpace(factory) },
            ContractCheck("the space is built once") { builtOnce(factory) },
        )

    private fun SoundFixtureSong.copyOf(
        key: String,
        inLibrary: Boolean = this.inLibrary,
        currentVersion: Boolean = this.currentVersion,
        fileUnchanged: Boolean = this.fileUnchanged,
    ) = SoundFixtureSong(
        key,
        centroid,
        bass,
        onset,
        beat,
        loudnessVariation,
        meanLoudness,
        inLibrary,
        currentVersion,
        fileUnchanged,
    )

    private fun space(source: SeededSoundSource): SoundSpace = runBlocking { source.source.space() }

    private fun expect(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) throw AssertionError(message())
    }

    private fun listsLibrary(factory: SoundSourceFactory) {
        val seeded = factory.create(library + outOfDate)
        val space = space(seeded)
        val expected = library.map { seeded.idOf(it.key) }.sorted()
        expect(space.songIds.toList() == expected) { "Expected songs $expected, got ${space.songIds.toList()}" }
        expect(space.dim == SoundVectorizer.DIM) { "Expected ${SoundVectorizer.DIM} dimensions, got ${space.dim}" }
        expect(space.values.size == space.size * space.dim) { "Values do not fit ${space.size} x ${space.dim}" }
    }

    private fun ascending(factory: SoundSourceFactory) {
        val space = space(factory.create(library.reversed()))
        val ids = space.songIds.toList()
        expect(ids == ids.sorted()) { "Rows are not in ascending id: $ids" }
    }

    private fun valuesInRange(factory: SoundSourceFactory) {
        val space = space(factory.create(library + extreme))
        val bad = space.values.firstOrNull { !it.isFinite() || it < 0f || it > 1f }
        expect(bad == null) { "A value is outside 0..1 or not finite: $bad" }
    }

    /** The in-library songs get the same vectors with or without [other], which the source must not use at all. */
    private fun leftOut(
        factory: SoundSourceFactory,
        other: SoundFixtureSong,
    ) {
        val seeded = factory.create(library + other)
        val space = space(seeded)
        expect(space.indexOf(seeded.idOf(other.key)) == -1) { "${other.key} should not be in the space" }
        expect(space.size == library.size) { "Expected ${library.size} songs, got ${space.size}" }
    }

    private fun leftOutDoNotNormalise(factory: SoundSourceFactory) {
        val plain = factory.create(library)
        val noisy = factory.create(library + outOfDate)
        val plainSpace = space(plain)
        val noisySpace = space(noisy)
        for (song in library) {
            val a = plainSpace.vector(plainSpace.indexOf(plain.idOf(song.key)))
            val b = noisySpace.vector(noisySpace.indexOf(noisy.idOf(song.key)))
            expect(a.contentEquals(b)) { "${song.key} moved: ${a.toList()} against ${b.toList()}" }
        }
    }

    private fun emptySpace(factory: SoundSourceFactory) {
        val space = space(factory.create(listOf(outOfDate[0])))
        expect(space.size == 0) { "Expected no songs, got ${space.size}" }
        expect(space.dim == SoundVectorizer.DIM) { "Expected ${SoundVectorizer.DIM} dimensions, got ${space.dim}" }
    }

    private fun builtOnce(factory: SoundSourceFactory) {
        val seeded = factory.create(library)
        expect(space(seeded) === space(seeded)) { "A second call built the space again" }
    }
}
