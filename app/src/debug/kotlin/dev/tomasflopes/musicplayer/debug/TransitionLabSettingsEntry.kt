package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import javax.inject.Inject

/** The row in Settings that opens the Transition lab. It exists only in debug builds (ADR 0019, 0093). */
class TransitionLabSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "transition_lab"
        override val order = ORDER

        @Composable
        override fun Content() {
            val context = LocalContext.current
            SettingRow(
                title = "Transition lab",
                value = "Plan, play and measure a DJ transition between two songs",
                onClick = { context.startActivity(Intent(context, TransitionLabActivity::class.java)) },
            )
        }

        private companion object {
            /** After Sound neighbours (1_003). */
            const val ORDER = 1_004
        }
    }
