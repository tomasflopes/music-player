package dev.tomasflopes.musicplayer.core.analysis

import dev.tomasflopes.musicplayer.core.analysis.contract.SeededSoundSource
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundFixtureSong
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundSourceFactory
import dev.tomasflopes.musicplayer.core.analysis.contract.SoundVectorSourceContract
import dev.tomasflopes.musicplayer.core.model.ports.SoundVectorSource
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** The in-memory [SoundVectorSource] of the `check` tests: filters like the Room reader, runs the real vectorizer. */
class FakeSoundVectorSource(
    private val songs: List<SoundFixtureSong>,
    private val ids: Map<String, Long>,
) : SoundVectorSource {
    private val built: SoundSpace by lazy {
        SoundVectorizer().vectorize(
            songs
                .filter { it.inLibrary && it.currentVersion && it.fileUnchanged }
                .map { RawSoundProfile(ids.getValue(it.key), it.fixed()) },
        )
    }

    override suspend fun space(): SoundSpace = built
}

/** The [SoundVectorSource] contract against the fake, in `check`. The Room twin runs in `deviceCheck`. */
class FakeSoundVectorSourceContractTest {
    @TestFactory
    fun `the fake meets the sound vector source contract`(): List<DynamicTest> =
        SoundVectorSourceContract
            .checks(
                SoundSourceFactory { songs ->
                    // Ids that do not follow the order of the songs, so the contract's ordering check means something.
                    val ids = songs.mapIndexed { index, song -> song.key to (100L - index * 7) }.toMap()
                    SeededSoundSource(FakeSoundVectorSource(songs, ids)) { ids.getValue(it) }
                },
            ).map { check -> DynamicTest.dynamicTest(check.name) { check.run() } }
}
