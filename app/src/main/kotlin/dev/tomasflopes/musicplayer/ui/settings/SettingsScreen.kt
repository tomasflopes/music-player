package dev.tomasflopes.musicplayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.ui.components.BackTitleRow
import dev.tomasflopes.musicplayer.ui.components.SectionHeader
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.DaysWindowSheet
import dev.tomasflopes.musicplayer.ui.home.dynamiclist.daysLabel

/**
 * Settings: the music folder and the Recently added window, then whatever entries are registered. [onPickFolder]
 * is the one folder-picker launcher of `MainActivity`; the folder it returns is saved and scanned by the shell.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackTitleRow(onBack, title = "Settings")
        SectionHeader("Library")
        SettingRow("Music folder", state.musicFolder ?: "Not set", onPickFolder)
        SettingRow("Recently added window", daysLabel(state.recentlyAdded.days), { pickerOpen = true })
        viewModel.entries.forEach { entry -> key(entry.id) { entry.Content() } }
    }

    if (pickerOpen) {
        DaysWindowSheet(
            window = state.recentlyAdded,
            onQuickOption = viewModel::onQuickDays,
            onCustom = viewModel::onCustomDays,
            onDismiss = {
                pickerOpen = false
                viewModel.onPickerDismissed()
            },
        )
    }
}
