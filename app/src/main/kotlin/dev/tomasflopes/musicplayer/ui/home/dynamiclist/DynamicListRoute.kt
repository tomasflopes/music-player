package dev.tomasflopes.musicplayer.ui.home.dynamiclist

/**
 * The screen of one dynamic list. A plain string route like the tabs' (ADR 0016); the list id is a slug
 * (`recently_added`), so it needs no URL encoding. Typed routes would need an ADR if they ever become necessary.
 */
object DynamicListRoute {
    const val ARG_ID = "id"
    const val PATTERN = "dynamic_list/{$ARG_ID}"

    fun create(listId: String) = "dynamic_list/$listId"
}
