package com.tilewarden.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.tilewarden.core.Barbarian
import com.tilewarden.core.Character
import com.tilewarden.core.Dwarf
import com.tilewarden.core.Goblin
import com.tilewarden.core.Hero
import com.tilewarden.core.Mummy

/**
 * 2D Canvas rendering of the game board.
 *
 * Replaces the ASCII text view from milestone 2. Each cell is a filled
 * square with a thin border; each character is a coloured disc with its
 * [Character.symbol] drawn in the centre.
 *
 * The visual style here is intentionally neutral — flat colours, no
 * texture, no sprite art — so we can lock in the rendering pipeline now
 * and swap to real sprites once we commit to a visual theme.
 */
@Composable
fun BoardCanvas(
    session: GameSession,
    modifier: Modifier = Modifier,
) {
    // Read observable state up front so the Canvas recomposes every time the
    // game advances. The values themselves aren't used — the read is what
    // subscribes us to invalidations.
    @Suppress("UNUSED_VARIABLE")
    val r = session.round

    val game = session.game
    val rows = game.board.rows
    val cols = game.board.columns

    val tileFill   = MaterialTheme.colorScheme.surface
    val tileBorder = MaterialTheme.colorScheme.outline
    val emptyDot   = MaterialTheme.colorScheme.surfaceVariant
    val symbolInk  = Color(0xFF1B1714)

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 16.sp,
        color = symbolInk,
        fontWeight = FontWeight.Bold,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cols.toFloat() / rows.toFloat()),
    ) {
        val tileSize = size.width / cols
        val borderPx = (tileSize * 0.04f).coerceAtLeast(1f)
        val pieceRadius = tileSize * 0.36f
        val emptyDotRadius = tileSize * 0.06f

        // Grid
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val topLeft = Offset(col * tileSize, row * tileSize)
                val cellSize = Size(tileSize, tileSize)
                drawRect(color = tileFill, topLeft = topLeft, size = cellSize)
                drawRect(
                    color = tileBorder,
                    topLeft = topLeft,
                    size = cellSize,
                    style = Stroke(width = borderPx),
                )
                // Subtle centre dot on empty cells so the grid reads as a board.
                drawCircle(
                    color = emptyDot,
                    radius = emptyDotRadius,
                    center = Offset(
                        col * tileSize + tileSize / 2f,
                        row * tileSize + tileSize / 2f,
                    ),
                )
            }
        }

        // Characters: column is x on the board, row is y; in screen space we
        // map board.row -> screen.y and board.column -> screen.x.
        for (c in game.characters) {
            val pos = c.position ?: continue
            val cx = pos.y * tileSize + tileSize / 2f
            val cy = pos.x * tileSize + tileSize / 2f

            drawCircle(
                color = pieceColor(c),
                radius = pieceRadius,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = symbolInk,
                radius = pieceRadius,
                center = Offset(cx, cy),
                style = Stroke(width = borderPx),
            )

            // Symbol letter, centred on the disc.
            val text = c.symbol.toString()
            val layout = measurer.measure(AnnotatedString(text), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    cx - layout.size.width / 2f,
                    cy - layout.size.height / 2f,
                ),
            )
        }
    }
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
