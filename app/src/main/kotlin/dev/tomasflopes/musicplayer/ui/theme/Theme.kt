package dev.tomasflopes.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFFF8A00)
private val OnOrange = Color(0xFF1A0F00)
private val OrangeContainer = Color(0xFF332200)
private val OnOrangeContainer = Color(0xFFFFD9A6)
private val Background = Color(0xFF0E0E0E)
private val Surface = Color(0xFF181818)
private val OnSurface = Color(0xFFEDEDED)

/** The app's one fixed scheme: dark background, orange accents, no light mode (design 8.1). */
private val MusicPlayerColors =
    darkColorScheme(
        primary = Orange,
        onPrimary = OnOrange,
        primaryContainer = OrangeContainer,
        onPrimaryContainer = OnOrangeContainer,
        secondary = Color(0xFF8A6D3B),
        onSecondary = Color(0xFFFFFFFF),
        // The navigation bar's selected indicator uses the secondary container; without these it would be purple.
        secondaryContainer = OrangeContainer,
        onSecondaryContainer = OnOrangeContainer,
        background = Background,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = Color(0xFF232323),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF3A3A3A),
        inverseSurface = OnSurface,
        inverseOnSurface = Color(0xFF1E1E1E),
        inversePrimary = Orange,
        surfaceDim = Color(0xFF131313),
        surfaceBright = Color(0xFF232323),
        surfaceContainerLowest = Background,
        surfaceContainerLow = Color(0xFF131313),
        surfaceContainer = Surface,
        surfaceContainerHigh = Color(0xFF232323),
        surfaceContainerHighest = Color(0xFF2D2D2D),
    )

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicPlayerColors,
        typography = MusicPlayerTypography,
        content = content,
    )
}
