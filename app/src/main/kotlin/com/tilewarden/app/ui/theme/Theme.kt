package com.tilewarden.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 colour scheme mapped onto the pixel-art warm palette
 * from [Color.kt]. Heavy lifting is in the assignments below; the
 * rest of the app just calls `MaterialTheme.colorScheme.X` and inherits.
 */
private val TilewardenDarkColors = darkColorScheme(
    primary               = GoldBright,
    onPrimary             = BgDeep,
    primaryContainer      = GoldDeep,
    onPrimaryContainer    = ParchmentLight,

    secondary             = Terracotta,
    onSecondary           = BgDeep,
    secondaryContainer    = Color(0xFF6A2A24),  // muted terracotta backdrop
    onSecondaryContainer  = ParchmentLight,

    tertiary              = MossGreen,
    onTertiary            = BgDeep,
    tertiaryContainer     = Color(0xFF3E5A30),
    onTertiaryContainer   = ParchmentLight,

    background            = BgDeep,
    onBackground          = ParchmentLight,
    surface               = SurfaceBrown,
    onSurface             = ParchmentLight,
    surfaceVariant        = SurfaceBrown2,
    onSurfaceVariant      = ParchmentDim,

    outline               = OutlineWarm,
    outlineVariant        = SurfaceBrown2,
)

// Default Material typography for now. When we add a pixel font we'll
// swap in custom font families here.
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
