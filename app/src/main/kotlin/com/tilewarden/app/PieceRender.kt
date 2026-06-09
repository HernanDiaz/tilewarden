package com.tilewarden.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tilewarden.core.Barbarian
import com.tilewarden.core.Character
import com.tilewarden.core.Dwarf
import com.tilewarden.core.Goblin
import com.tilewarden.core.Hero
import com.tilewarden.core.Mummy

/**
 * Plain-data view of a single character at one moment.
 *
 * Mutated piece-by-piece during event replay (see [GameSession]) so the
 * Canvas and HUD recompose only for the entity that actually changed.
 */
@Immutable
data class PieceRender(
    val row: Int,
    val column: Int,
    val symbol: Char,
    val color: Color,
    val name: String,
    val body: Int,
    val initialBody: Int,
    val isHero: Boolean,
) {
    /** 0..1, the proportion of body remaining. */
    val healthRatio: Float
        get() = if (initialBody <= 0) 0f
                else (body.toFloat() / initialBody.toFloat()).coerceIn(0f, 1f)
}

/**
 * Build a [PieceRender] from a live [Character]. Used by [GameSession] at
 * startup and reset; during a round the renders are mutated incrementally
 * from individual [com.tilewarden.core.GameEvent]s, not rebuilt wholesale.
 */
internal fun renderOf(character: Character): PieceRender? {
    val pos = character.position ?: return null
    return PieceRender(
        row = pos.x,
        column = pos.y,
        symbol = character.symbol,
        color = pieceColor(character),
        name = character.name,
        body = character.body,
        initialBody = character.initialBody,
        isHero = character is Hero,
    )
}

/** Placeholder palette — replaced when we commit to a final visual theme. */
private fun pieceColor(c: Character): Color = when (c) {
    is Barbarian -> Color(0xFFE0B355)  // amber / brass
    is Dwarf     -> Color(0xFFC07A3D)  // copper
    is Goblin    -> Color(0xFF8FB04A)  // olive green
    is Mummy     -> Color(0xFFE0D6C2)  // bandages
    is Hero      -> Color(0xFFD4A04A)  // future hero default
    else         -> Color(0xFF8C7B6A)  // future neutral / monster default
}
