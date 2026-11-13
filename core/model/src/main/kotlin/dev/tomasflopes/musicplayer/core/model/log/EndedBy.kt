package dev.tomasflopes.musicplayer.core.model.log

/** How a play ended, as stored in `play_event.ended_by`. */
enum class EndedBy(
    val wire: String,
) {
    COMPLETED("completed"),
    SKIPPED("skipped"),
    STOPPED("stopped"),
    ;

    companion object {
        fun fromWire(wire: String): EndedBy = entries.first { it.wire == wire }
    }
}
