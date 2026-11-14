package dev.tomasflopes.musicplayer.core.taste.config

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Audit 2026-11-14, S11: a rule with a nonsense value is refused when it is built, not scored into NaN. */
class TasteConfigAuditTest {
    private fun refused(build: () -> Any) {
        assertThrows(IllegalArgumentException::class.java) { build() }
    }

    @Test
    fun `S11 half life must be positive and finite`() {
        refused { DecayRule(halfLifeDays = 0.0) }
        refused { DecayRule(halfLifeDays = -30.0) }
        refused { DecayRule(halfLifeDays = Double.NaN) }
        refused { DecayRule(halfLifeDays = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `S11 horizon must be positive`() {
        refused { DecayRule(horizonDays = 0) }
        refused { DecayRule(horizonDays = -1) }
    }

    @Test
    fun `S11 burst rule needs a skip count, a softening between 0 and 1 and a gap`() {
        refused { BurstRule(minSkips = 0) }
        refused { BurstRule(softening = -0.1) }
        refused { BurstRule(softening = 1.5) }
        refused { BurstRule(maxGapMs = -1) }
    }

    @Test
    fun `S11 top list needs at least one slot and a finite minimum score`() {
        refused { TopListRule(size = 0) }
        refused { TopListRule(size = -5) }
        refused { TopListRule(minScore = Double.NaN) }
    }

    @Test
    fun `S11 signal weights must be finite and the fractions ordered inside 0 to 1`() {
        refused { SignalWeights(picked = Double.NaN) }
        refused { SignalWeights(skipEarly = Double.NEGATIVE_INFINITY) }
        refused { SignalWeights(earlySkipMs = -1) }
        refused { SignalWeights(midSkipFraction = -0.1) }
        refused { SignalWeights(completedFraction = 1.2) }
        refused { SignalWeights(midSkipFraction = 0.9, completedFraction = 0.8) }
    }

    @Test
    fun `artist and search rules refuse nonsense`() {
        refused { ArtistRule(featuredShare = -0.1) }
        refused { ArtistRule(featuredShare = 1.1) }
        refused { ArtistRule(shuffleStarted = Double.NaN) }
        refused { ArtistRule(shuffleGapMs = -1) }
        refused { SearchRule(song = Double.NaN) }
        refused { SearchRule(artist = Double.POSITIVE_INFINITY) }
        refused { SignalWeights(addedToPlaylist = Double.NaN) }
    }

    @Test
    fun `S11 the defaults are valid`() {
        TasteConfig()
    }
}
