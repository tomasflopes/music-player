package dev.tomasflopes.musicplayer.core.search

/**
 * One field's matching rule. New fields or smarter rules are added as new implementations and registered
 * beside the default ones; [SearchIndex] never needs a `when` over fields.
 *
 * [queryVariants] is the folded query once per search, so an implementation does not re-fold it per document.
 */
interface SearchMatcher {
    val field: SearchField

    fun match(
        queryVariants: Set<String>,
        value: String,
    ): MatchTier?
}

/** The default rule: exact, then whole-word / starts-with, then mid-word substring. */
class FieldMatcher(
    override val field: SearchField,
) : SearchMatcher {
    override fun match(
        queryVariants: Set<String>,
        value: String,
    ): MatchTier? {
        val valueVariants = TextNormalizer.variants(value)
        var best: MatchTier? = null
        for (query in queryVariants) {
            for (variant in valueVariants) {
                val tier = tier(query, variant) ?: continue
                if (best == null || tier.rank > best.rank) best = tier
            }
        }
        return best
    }

    private fun tier(
        query: String,
        value: String,
    ): MatchTier? =
        when {
            value == query -> MatchTier.EXACT
            value.startsWith(query) || value.contains(" $query") -> MatchTier.STARTS_WITH
            value.contains(query) -> MatchTier.CONTAINS
            else -> null
        }

    companion object {
        /** One default matcher per field. */
        fun defaults(): Set<SearchMatcher> = SearchField.entries.map(::FieldMatcher).toSet()
    }
}
