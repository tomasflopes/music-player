package dev.tomasflopes.musicplayer.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.playback.lab.LabReading
import dev.tomasflopes.musicplayer.playback.lab.LabSummary
import java.util.Locale

/**
 * Debug, Transition lab (ADR 0093): two songs, the plan the planner makes, and a live reading while the lab plays it.
 * The numbers the owner reports from the phone are in the summary: start alignment error, drift, and the speed log.
 */
@Composable
fun TransitionLabScreen(viewModel: TransitionLabViewModel) {
    val state by viewModel.state.collectAsState()
    val lab by viewModel.labState.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Transition lab", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotChip("Outgoing", state.picking == LabSlot.OUTGOING) { viewModel.onSlot(LabSlot.OUTGOING) }
                SlotChip("Incoming", state.picking == LabSlot.INCOMING) { viewModel.onSlot(LabSlot.INCOMING) }
                OutlinedButton(onClick = viewModel::onCurrentAndNext) { Text("Current and next") }
            }
        }
        item { SongSlot("outgoing", state.outgoing) }
        item { SongSlot("incoming", state.incoming) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search a song for the chosen slot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.matches) { song ->
            Text(
                "${song.title} - ${song.artistName}",
                Modifier.fillMaxWidth().clickable { viewModel.onSongPicked(song) },
            )
        }
        items(state.planLines) { line -> Mono(line) }
        item { Controls(viewModel, state.canPlay) }
        item { Toggles() }
        item { Reading(lab.reading) }
        item { Summary(lab.summary) }
        if (lab.message.isNotEmpty()) item { Text(lab.message) }
        item { Text("Speed changes", style = MaterialTheme.typography.titleSmall) }
        items(lab.speedLog) { Mono(it) }
    }
}

@Composable
private fun SlotChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) = FilterChip(selected = selected, onClick = onClick, label = { Text(label) })

@Composable
private fun SongSlot(
    slot: String,
    song: Song?,
) = Text("$slot: ${song?.let { "${it.title} - ${it.artistName}" } ?: "not chosen"}")

@Composable
private fun Controls(
    viewModel: TransitionLabViewModel,
    canPlay: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::onPlay, enabled = canPlay) { Text("Play transition") }
            OutlinedButton(onClick = viewModel::onStop) { Text("Stop") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::onDriftTest, enabled = canPlay) { Text("Drift test 30 s") }
            OutlinedButton(onClick = viewModel::onSpeedChange) { Text("Speed 1.0, 1.04, 1.0") }
        }
    }
}

/** Slice 1 has neither a bass swap nor a phase lock; the switches are here so the lab keeps its final layout. */
@Composable
private fun Toggles() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = false, onCheckedChange = null, enabled = false)
            Text("Bass swap (slice 3)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = false, onCheckedChange = null, enabled = false)
            Text("Phase lock (slice 2)")
        }
    }
}

@Composable
private fun Reading(reading: LabReading) {
    Mono(
        String.format(
            Locale.ROOT,
            "%s  out %d ms  in %d ms  phase error %s ms  speed %.3f",
            reading.phase,
            reading.outgoingMs,
            reading.incomingMs,
            number(reading.phaseErrorMs),
            reading.incomingSpeed,
        ),
    )
}

@Composable
private fun Summary(summary: LabSummary) {
    Mono(
        "start error ${number(summary.startErrorMs)} ms, end error ${number(summary.endErrorMs)} ms, " +
            "drift ${number(summary.driftMs)} ms, max |error| ${number(summary.maxAbsErrorMs)} ms",
    )
}

@Composable
private fun Mono(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

private fun number(value: Double) = if (value.isNaN()) "-" else String.format(Locale.ROOT, "%.1f", value)
