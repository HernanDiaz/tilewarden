package com.tilewarden.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TilewardenDarkColors = darkColorScheme(
    primary           = Amber500,
    onPrimary         = DarkBg,
    primaryContainer  = Amber700,
    onPrimaryContainer = OnDark,
    secondary         = Amber200,
    onSecondary       = DarkBg,
    background        = DarkBg,
    onBackground      = OnDark,
    surface           = DarkSurface,
    onSurface         = OnDark,
    surfaceVariant    = DarkSurface2,
    onSurfaceVariant  = OnDarkMuted,
)

// Default Material typography for now. When we commit to a visual theme
// we'll swap in custom font families here.
private val TilewardenTypography = Typography()

@Composable
fun TilewardenTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TilewardenDarkColors,
        typography  = TilewardenTypography,
        content     = content,
    )
}
