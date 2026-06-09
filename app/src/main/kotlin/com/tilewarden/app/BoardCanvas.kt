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

/**
 * 2D rendering of a [BoardSnapshot].
 *
 * Pure data-in → pixels-out: the Composable depends only on its [snapshot]
 * parameter, so Compose recomposes it (and the Canvas redraws) exactly when
 * the snapshot's value changes. No engine references leak in.
 */
@Composable
fun BoardCanvas(
    snapshot: BoardSnapshot,
    modifier: Modifier = Modifier,
) {
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
            .aspectRatio(snapshot.columns.toFloat() / snapshot.rows.toFloat()),
    ) {
        val cols = snapshot.columns
        val rows = snapshot.rows
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

        // Pieces — board.row maps to screen.y, board.column maps to screen.x.
        for (piece in snapshot.pieces) {
            val cx = piece.column * tileSize + tileSize / 2f
            val cy = piece.row    * tileSize + tileSize / 2f

            drawCircle(
                color = piece.color,
                radius = pieceRadius,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = symbolInk,
                radius = pieceRadius,
                center = Offset(cx, cy),
                style = Stroke(width = borderPx),
            )

            val layout = measurer.measure(AnnotatedString(piece.symbol.toString()), labelStyle)
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
