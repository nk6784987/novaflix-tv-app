package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = CrimsonRed,
  onPrimary = Color.White,
  primaryContainer = SurfaceContainerDark,
  onPrimaryContainer = VibrantRed,
  secondary = NeonCyan,
  onSecondary = PitchBlack,
  secondaryContainer = SurfaceContainerDark,
  onSecondaryContainer = NeonCyan,
  tertiary = GoldenYellow,
  background = PitchBlack,
  onBackground = TextPrimary,
  surface = SurfaceDark,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceContainerDark,
  onSurfaceVariant = TextSecondary,
  outline = OutlineDark,
  surfaceContainer = SurfaceContainerDark,
  surfaceContainerHigh = SurfaceContainerHigh
)

@Composable
fun CineStreamTheme(
  darkTheme: Boolean = true, // Streaming app defaults to dark mode
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}

