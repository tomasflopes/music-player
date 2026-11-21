package dev.tomasflopes.musicplayer.fakes

import dev.tomasflopes.musicplayer.core.model.mixes.MixId
import dev.tomasflopes.musicplayer.core.taste.mix.MixLearning
import dev.tomasflopes.musicplayer.core.taste.mix.MixScoreBoard

/** A mix score board with a fixed answer that remembers what it was asked (ADR 0085). */
class FakeMixScoreBoard(
    var learning: MixLearning = MixLearning.EMPTY,
) : MixScoreBoard {
    val asked = mutableListOf<Pair<MixId, Long>>()

    override suspend fun of(
        mixId: MixId,
        now: Long,
    ): MixLearning {
        asked += mixId to now
        return learning
    }
}
