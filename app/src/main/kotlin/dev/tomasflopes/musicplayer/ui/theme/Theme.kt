package dev.tomasflopes.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF8A00)
private val OnOrange = Color(0xFF1A0F00)
private val Background = Color(0xFF0E0E0E)
private val Surface = Color(0xFF181818)

private val MusicPlayerColors =
    darkColorScheme(
        primary = Orange,
        onPrimary = OnOrange,
        background = Background,
        onBackground = Color(0xFFEDEDED),
        surface = Surface,
        onSurface = Color(0xFFEDEDED),
    )

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MusicPlayerColors, content = content)
}
