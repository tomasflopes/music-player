package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.settings.SettingsEntry
import javax.inject.Inject

/** The row in Settings that opens the analysis benchmark. It exists only in debug builds (ADR 0019). */
class AnalysisBenchmarkSettingsEntry
    @Inject
    constructor() : SettingsEntry {
        override val id = "analysis_benchmark"
        override val order = ORDER

        @Composable
        override fun Content() {
            val context = LocalContext.current
            SettingRow(
                title = "Analysis benchmark",
                value = "Decode and spectrum timing on 60 songs",
                onClick = { context.startActivity(Intent(context, AnalysisBenchmarkActivity::class.java)) },
            )
        }

        private companion object {
            /** Right after the debug screen entry. */
            const val ORDER = 1_001
        }
    }
