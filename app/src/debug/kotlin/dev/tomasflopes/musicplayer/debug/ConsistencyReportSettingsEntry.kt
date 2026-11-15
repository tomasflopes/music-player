package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import javax.inject.Inject

/** The row in Settings that opens the consistency report. It exists only in debug builds (ADR 0019). */
class ConsistencyReportSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "consistency_report"
        override val order = ORDER

        @Composable
        override fun Content() {
            val context = LocalContext.current
            SettingRow(
                title = "Consistency report",
                value = "Re-analyse 20 imported songs, largest gap per feature",
                onClick = { context.startActivity(Intent(context, ConsistencyReportActivity::class.java)) },
            )
        }

        private companion object {
            /** After the analysis benchmark (1_001). */
            const val ORDER = 1_002
        }
    }
