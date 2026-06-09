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
 * Canvas and other consumers recompose only for the entity that actually
 * changed.
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
    val moves: Int,
    val attack: Int,
    val defense: Int,
    val weaponName: String? = null,
    val weaponDice: Int? = null,
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
    val weapon = (character as? Hero)?.weapon
    return PieceRender(
        row = pos.x,
        column = pos.y,
        symbol = character.symbol,
        color = pieceColor(character),
        name = character.name,
        body = character.body,
        initialBody = character.initialBody,
        isHero = character is Hero,
        moves = character.moves,
        attack = character.attack,
        defense = character.defense,
        weaponName = weapon?.name,
        weaponDice = weapon?.attackDice,
    )
}

/**
 * Subclass tints in the pixel-art warm palette.
 * The actual values live in [com.tilewarden.app.ui.theme] so colour
 * decisions stay in one place.
 */
private fun pieceColor(c: Character): Color = when (c) {
    is Barbarian -> com.tilewarden.app.ui.theme.BarbarianTint
    is Dwarf     -> com.tilewarden.app.ui.theme.DwarfTint
    is Goblin    -> com.tilewarden.app.ui.theme.GoblinTint
    is Mummy     -> com.tilewarden.app.ui.theme.MummyTint
    is Hero      -> com.tilewarden.app.ui.theme.GoldBright
    else         -> com.tilewarden.app.ui.theme.SurfaceBrown2
}
