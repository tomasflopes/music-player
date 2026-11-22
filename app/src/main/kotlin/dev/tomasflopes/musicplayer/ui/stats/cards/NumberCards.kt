package dev.tomasflopes.musicplayer.ui.stats.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.core.taste.stats.ListeningClock
import dev.tomasflopes.musicplayer.ui.components.AlbumArt
import dev.tomasflopes.musicplayer.ui.components.ArtistAvatar
import dev.tomasflopes.musicplayer.ui.stats.WrappedCard
import dev.tomasflopes.musicplayer.ui.stats.WrappedData
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import java.text.NumberFormat
import javax.inject.Inject

private const val MINUTES_PER_HOUR = 60
private const val BAR_AREA_DP = 96
private const val MIN_BAR_DP = 3

/** "21:00": the hour as a clock reads it, for the listening clock's caption. */
internal fun hourText(hour: Int): String = "%02d:00".format(hour)

class MinutesListenedCard
    @Inject
    constructor() : WrappedCard {
        override val id = "minutes"
        override val order = 40

        override fun hasContent(data: WrappedData) = data.stats.minutesListened > 0

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) {
            val minutes = data.stats.minutesListened
            WrappedCardFrame("Minutes listened", modifier) {
                BigNumber(NumberFormat.getIntegerInstance().format(minutes))
                Caption(
                    if (minutes >=
                        MINUTES_PER_HOUR
                    ) {
                        "That is about ${minutes / MINUTES_PER_HOUR} hours of music."
                    } else {
                        "minutes of music."
                    },
                )
            }
        }
    }

class NewDiscoveriesCard
    @Inject
    constructor() : WrappedCard {
        override val id = "discoveries"
        override val order = 50

        override fun hasContent(data: WrappedData) = data.stats.newDiscoveries.count > 0

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("New discoveries", modifier) {
            BigNumber(NumberFormat.getIntegerInstance().format(data.stats.newDiscoveries.count))
            Caption("songs you played for the first time ever.")
            data.discoveries.forEach { song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(song, ArtSize.row)
                    Spacer(Modifier.width(Spacing.xs))
                    Column {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

class ListeningClockCard
    @Inject
    constructor() : WrappedCard {
        override val id = "clock"
        override val order = 70

        override fun hasContent(data: WrappedData) = data.stats.clock.total > 0

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Listening clock", modifier) {
            val clock = data.stats.clock
            clock.peakHour?.let {
                Text(
                    "You listen most around ${hourText(it)}.",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            HourBars(clock)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18).forEach { Caption(hourText(it)) }
            }
        }
    }

/** One bar per hour of the day, tallest for the busiest, the busiest in orange. */
@Composable
private fun HourBars(clock: ListeningClock) {
    val peak = clock.playsByHour.max()
    Row(
        modifier = Modifier.fillMaxWidth().height(BAR_AREA_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        clock.playsByHour.forEach { plays ->
            val share = if (peak == 0) 0f else plays.toFloat() / peak
            val color =
                if (plays == peak &&
                    peak > 0
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            Box(
                Modifier
                    .weight(1f)
                    .height(maxOf(MIN_BAR_DP.dp, (BAR_AREA_DP * share).dp))
                    .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

class ComebackArtistCard
    @Inject
    constructor() : WrappedCard {
        override val id = "comeback"
        override val order = 80

        override fun hasContent(data: WrappedData) = data.comeback != null

        @Composable
        override fun Content(
            data: WrappedData,
            modifier: Modifier,
        ) = WrappedCardFrame("Biggest comeback", modifier) {
            data.comeback?.let { comeback ->
                ArtistAvatar(comeback.artist.artistId, comeback.artist.name, ArtSize.header)
                Text(
                    comeback.artist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Caption("${comeback.artist.count} plays, up from ${comeback.previousPlays} the period before.")
            }
        }
    }
