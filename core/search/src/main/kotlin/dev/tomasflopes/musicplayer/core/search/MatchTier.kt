package dev.tomasflopes.musicplayer.core.search

/** How strongly a field matched the query; higher tiers rank first. */
@Suppress("MagicNumber")
enum class MatchTier(
    val rank: Int,
) {
    EXACT(3),
    STARTS_WITH(2),
    CONTAINS(1),
}
