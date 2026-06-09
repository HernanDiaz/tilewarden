package com.tilewarden.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tilewarden.core.Barbarian
import com.tilewarden.core.Character
import com.tilewarden.core.Dwarf
import com.tilewarden.core.Game
import com.tilewarden.core.Goblin
import com.tilewarden.core.Hero
import com.tilewarden.core.Monster
import com.tilewarden.core.Mummy

/**
 * Plain-data snapshot of the board at one instant.
 *
 * Marked [Immutable] so Compose can compare it by value (data class equality)
 * and skip recomposition when nothing changed. Each call to [from] produces
 * a fresh instance; pass it as the parameter to [BoardCanvas] and Compose
 * will re-draw exactly when the underlying game has moved.
 */
@Immutable
data class BoardSnapshot(
    val rows: Int,
    val columns: Int,
    val pieces: List<PieceRender>,
    val heroesAlive: Int,
    val monstersAlive: Int,
) {
    companion object {
        fun from(game: Game): BoardSnapshot {
            val pieces = game.characters.mapNotNull { c ->
                c.position?.let { pos ->
                    PieceRender(
                        row = pos.x,
                        column = pos.y,
                        symbol = c.symbol,
                        color = pieceColor(c),
                        name = c.name,
                        body = c.body,
                        initialBody = c.initialBody,
                        isHero = c is Hero,
                    )
                }
            }
            return BoardSnapshot(
                rows = game.board.rows,
                columns = game.board.columns,
                pieces = pieces,
                heroesAlive = game.characters.count { it is Hero },
                monstersAlive = game.characters.count { it is Monster },
            )
        }
    }
}

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
    /** 0..1, the proportion of body remaining. Capped to that range. */
    val healthRatio: Float
        get() = if (initialBody <= 0) 0f
                else (body.toFloat() / initialBody.toFloat()).coerceIn(0f, 1f)
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
