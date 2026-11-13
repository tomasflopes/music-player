package dev.tomasflopes.musicplayer.core.model.log

/**
 * Why this song started (ADR 0008): the owner tapped it, the previous one ended, or next/previous was pressed.
 * Lets later phases score a "tapped directly" play (design §7.4) without guessing.
 */
enum class StartedBy(
    val wire: String,
) {
    PICKED("picked"),
    AUTO("auto"),
    NAV("nav"),
    ;

    companion object {
        fun fromWire(wire: String): StartedBy = entries.first { it.wire == wire }
    }
}
