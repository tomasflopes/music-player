package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.log.PlaySource

/** Why a typed-in number of days was not applied. */
enum class DaysError { NOT_A_NUMBER, OUT_OF_RANGE }

/** The "Last N days" setting of a list, with what the picker offers and the last rejected input's error. */
data class DaysWindow(
    val days: Int,
    val quickOptions: List<Int>,
    val range: IntRange,
    val error: DaysError?,
)

sealed interface DynamicListUiState {
    /** No list opened yet. */
    data object Loading : DynamicListUiState

    /** The route named a list no provider is registered for. */
    data object NotFound : DynamicListUiState

    /** [windowDays] is null for lists that have no days window; it is changed in Settings, not on the list. */
    data class Ready(
        val title: String,
        val songs: List<Song>,
        val playSource: PlaySource,
        val windowDays: Int?,
    ) : DynamicListUiState
}
