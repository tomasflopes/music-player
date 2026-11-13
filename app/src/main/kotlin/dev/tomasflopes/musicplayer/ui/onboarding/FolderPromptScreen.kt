package dev.tomasflopes.musicplayer.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** Stand-in for onboarding (plan Task 23): until the owner picks a music folder there is nothing to show. */
@Composable
fun FolderPromptScreen(onPickFolder: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text("Pick your music folder", style = MaterialTheme.typography.titleLarge)
            Button(onPickFolder) { Text("Choose folder") }
        }
    }
}
