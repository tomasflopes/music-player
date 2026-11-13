package dev.tomasflopes.musicplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The type hierarchy: clear steps between titles and body text, in the Spotify / Apple Music direction. */
val MusicPlayerTypography =
    Typography(
        displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
        displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
