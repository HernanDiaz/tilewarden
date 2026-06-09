package com.tilewarden.app

import androidx.compose.ui.graphics.Color

/**
 * Team-level palette — used as a halo around each piece so it's
 * immediately obvious who plays for whom. Independent of the per-subclass
 * tint (amber/copper/olive/bandage) which still distinguishes Barbarian
 * vs Dwarf vs Goblin vs Mummy.
 */
internal val TEAM_HERO_COLOR    = Color(0xFF3C6FB0)  // steel blue
internal val TEAM_MONSTER_COLOR = Color(0xFF8C2E4E)  // burgundy / dark crimson

/** Faded opacity for heroes that have already taken their turn this round. */
internal const val ACTED_ALPHA = 0.45f

internal fun teamColor(isHero: Boolean): Color =
    if (isHero) TEAM_HERO_COLOR else TEAM_MONSTER_COLOR
