package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.lists.DynamicListProvider
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.ports.SettingsRepository
import dev.tomasflopes.musicplayer.core.model.ports.SongReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock

/** Songs added in the last N days (N from Settings), newest first. The first dynamic list. */
class RecentlyAddedProvider(
    private val songs: SongReader,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : DynamicListProvider {
    override val id = ID
    override val title = "Recently added"
    override val order = 0
    override val playSource = PlaySource.RECENTLY_ADDED

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun songs(): Flow<List<Song>> =
        settings.recentlyAddedDays
            .distinctUntilChanged()
            .flatMapLatest { days -> songs.observeAddedSince(clock.instant().epochSecond - days * SECONDS_PER_DAY) }

    companion object {
        /** Also the route argument of its screen, which shows the days window only for this list. */
        const val ID = "recently_added"
        private const val SECONDS_PER_DAY = 86_400L
    }
}
