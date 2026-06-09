package com.tilewarden.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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

private const val MOVE_ANIMATION_MS = 400

/**
 * 2D rendering of a [BoardSnapshot] with animated piece movement.
 *
 * Each piece's (column, row) is tracked through Compose's `animateFloatAsState`,
 * so when the snapshot updates with a new position the disc slides smoothly
 * to its destination over [MOVE_ANIMATION_MS] ms instead of teleporting.
 * The `key(piece.name)` wrapper gives each piece a stable identity in the
 * slot table even when other pieces around it disappear (deaths).
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

    // Per-piece animated coordinates. The `key(piece.name)` ensures each piece
    // gets its own animation state slot regardless of list reorderings.
    val animatedCoords: List<Triple<PieceRender, Float, Float>> =
        snapshot.pieces.map { piece ->
            key(piece.name) {
                val animatedColumn by animateFloatAsState(
                    targetValue = piece.column.toFloat(),
                    animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                    label = "col_${piece.name}",
                )
                val animatedRow by animateFloatAsState(
                    targetValue = piece.row.toFloat(),
                    animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                    label = "row_${piece.name}",
                )
                Triple(piece, animatedColumn, animatedRow)
            }
        }

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

        // Grid background — empty squares first, so pieces sit on top.
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

        // Pieces — using the animated (column, row) so motion is smooth.
        for ((piece, animCol, animRow) in animatedCoords) {
            val cx = animCol * tileSize + tileSize / 2f
            val cy = animRow * tileSize + tileSize / 2f

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
