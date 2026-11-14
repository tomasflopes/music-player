package dev.tomasflopes.musicplayer.core.analysis.contract

/** Where a test run finds the generated MP3s and their reference (`testdata/analysis`, `scripts/gen-test-mp3s.sh`). */
interface ContractFixtures {
    /** The location a [dev.tomasflopes.musicplayer.core.analysis.PcmSource] can open for the fixture called [name]. */
    fun location(name: String): String

    /** A location that does not exist. */
    fun missingLocation(): String

    /** The text of `reference.tsv`. */
    fun referenceText(): String
}

/** One named assertion of the contract; [run] throws [AssertionError] when it fails. */
class ContractCheck(
    val name: String,
    val run: () -> Unit,
)
