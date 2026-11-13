package dev.tomasflopes.musicplayer.core.tags

sealed interface TagReadResult {
    /** [warnings] list what was odd but survivable (a skipped frame, a multi-value artist). */
    data class Success(
        val tags: RawTags,
        val warnings: List<String>,
    ) : TagReadResult

    /** The file could not be read as tagged audio at all. */
    data class Failure(
        val reason: String,
    ) : TagReadResult
}

/** Reads the tags of one file. Never throws: a broken file is a [TagReadResult.Failure]. */
interface TagReader {
    fun read(source: TagSource): TagReadResult
}
