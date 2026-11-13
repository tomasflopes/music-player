package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import javax.inject.Inject

/** The row in Settings that opens the debug screen. It exists only in debug builds (ADR 0019). */
class DebugSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "debug_screen"
        override val order = ORDER

        @Composable
        override fun Content() {
            val context = LocalContext.current
            SettingRow(
                title = "Debug screen",
                value = "Folder, scan, playback and listening log",
                onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) },
            )
        }

        private companion object {
            /** After every built-in entry. */
            const val ORDER = 1_000
        }
    }
