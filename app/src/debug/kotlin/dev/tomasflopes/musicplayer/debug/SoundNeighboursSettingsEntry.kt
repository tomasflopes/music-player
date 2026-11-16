package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import javax.inject.Inject

/** The row in Settings that opens Sound neighbours. It exists only in debug builds (ADR 0019, 0073). */
class SoundNeighboursSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "sound_neighbours"
        override val order = ORDER

        @Composable
        override fun Content() {
            val context = LocalContext.current
            SettingRow(
                title = "Sound neighbours",
                value = "Pick a song, see the 20 that sound closest",
                onClick = { context.startActivity(Intent(context, SoundNeighboursActivity::class.java)) },
            )
        }

        private companion object {
            /** After the consistency report (1_002). */
            const val ORDER = 1_003
        }
    }
