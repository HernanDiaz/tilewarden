package com.tilewarden.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pixel-art warm palette.
 *
 * Inspired by classic dungeon-crawler tilesets (Kenney's Tiny Dungeon
 * pack, the Dragon's Crown / Diablo II warm chrome family). All values
 * are flat — no gradients — so they stay coherent once we start drawing
 * actual pixel-art sprites on the board.
 */

// Backgrounds & surfaces — warm browns, no neutrals.
val BgDeep        = Color(0xFF2A1A14)  // very dark warm brown
val SurfaceBrown  = Color(0xFF3D2A1F)  // panel card background
val SurfaceBrown2 = Color(0xFF5A3F2D)  // raised / variant
val OutlineWarm   = Color(0xFF7A5840)  // dividers & strokes

// Accents — gold first, terracotta for alerts, moss-green for life.
val GoldBright    = Color(0xFFF0C969)
val GoldDeep      = Color(0xFFA87838)
val Terracotta    = Color(0xFFE25D4A)
val MossGreen     = Color(0xFF7A9F5A)

// Text / iconography readable on the warm dark base.
val ParchmentLight = Color(0xFFF5E5C8)
val ParchmentMid   = Color(0xFFE8D4B0)
val ParchmentDim   = Color(0xFFB89770)

// Subclass tints — slightly punchier so they read clearly against the new
// warm board surface (the previous ones were tuned for neutral grey).
val BarbarianTint  = Color(0xFFE26A3E)  // warrior red-orange
val DwarfTint      = Color(0xFFC0813C)  // bronze
val GoblinTint     = Color(0xFF8FB04A)  // olive (kept — still reads as 'monster green')
val MummyTint      = Color(0xFFE6D49B)  // sandy parchment
