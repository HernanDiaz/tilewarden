package com.tilewarden.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MOVE_ANIMATION_MS = 400
private val ATTACK_BORDER_COLOR = Color(0xFFFF5040)
private val DAMAGE_BUBBLE_COLOR = Color(0xFFE04A4A)

/**
 * 2D rendering of the board, with per-piece movement animation, attack
 * highlight, death fade-out, and floating damage numbers.
 *
 * The Canvas itself paints the grid + pieces; floating damage labels are
 * regular Composables overlaid on top of the same [BoxWithConstraints], so
 * we can position them with Dp and let them animate as separate widgets.
 */
@Composable
fun BoardCanvas(
    rows: Int,
    columns: Int,
    pieces: List<PieceRender>,
    dyingPieces: List<String>,
    attackingPieces: List<String>,
    damageBubbles: List<DamageBubble>,
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

    // Animated state per piece, keyed by stable name so peers' deaths don't
    // disrupt the others' animation slots.
    val animatedPieces: List<AnimatedPiece> = pieces.map { piece ->
        key(piece.name) {
            val animCol by animateFloatAsState(
                targetValue = piece.column.toFloat(),
                animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                label = "col_${piece.name}",
            )
            val animRow by animateFloatAsState(
                targetValue = piece.row.toFloat(),
                animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                label = "row_${piece.name}",
            )
            val targetAlpha = if (piece.name in dyingPieces) 0f else 1f
            val animAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(DEATH_FADE_MS),
                label = "alpha_${piece.name}",
            )
            AnimatedPiece(piece, animCol, animRow, animAlpha)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / rows.toFloat()),
    ) {
        val tileSize: Dp = maxWidth / columns

        Canvas(modifier = Modifier.fillMaxSize()) {
            val tileSizePx = size.width / columns
            val borderPx = (tileSizePx * 0.04f).coerceAtLeast(1f)
            val attackBorderPx = borderPx * 2.5f
            val pieceRadius = tileSizePx * 0.36f
            val emptyDotRadius = tileSizePx * 0.06f

            // Grid.
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val topLeft = Offset(col * tileSizePx, row * tileSizePx)
                    val cellSize = Size(tileSizePx, tileSizePx)
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
                            col * tileSizePx + tileSizePx / 2f,
                            row * tileSizePx + tileSizePx / 2f,
                        ),
                    )
                }
            }

            // Pieces.
            for (ap in animatedPieces) {
                val piece = ap.piece
                val alpha = ap.alpha
                val cx = ap.column * tileSizePx + tileSizePx / 2f
                val cy = ap.row    * tileSizePx + tileSizePx / 2f

                drawCircle(
                    color = piece.color.copy(alpha = alpha),
                    radius = pieceRadius,
                    center = Offset(cx, cy),
                )
                val isAttacking = piece.name in attackingPieces
                val outlineColor = if (isAttacking) ATTACK_BORDER_COLOR else symbolInk
                val outlineWidth = if (isAttacking) attackBorderPx else borderPx
                drawCircle(
                    color = outlineColor.copy(alpha = alpha),
                    radius = pieceRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = outlineWidth),
                )

                val layout = measurer.measure(
                    AnnotatedString(piece.symbol.toString()),
                    labelStyle,
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        cx - layout.size.width / 2f,
                        cy - layout.size.height / 2f,
                    ),
                    alpha = alpha,
                )
            }
        }

        // Floating "-N" damage labels above wounded pieces. Live as long as
        // their entry stays in damageBubbles; once the session removes one
        // its key disappears and the Composable is torn down.
        for (bubble in damageBubbles) {
            key(bubble.id) {
                DamageBubbleOverlay(bubble = bubble, tileSize = tileSize)
            }
        }
    }
}

/** Returned from the animated-pieces builder so the Canvas can read scalar floats. */
private data class AnimatedPiece(
    val piece: PieceRender,
    val column: Float,
    val row: Float,
    val alpha: Float,
)

/**
 * A single damage label that floats up and fades over [BUBBLE_LIFETIME_MS].
 * Positioned in Dp relative to the parent [BoxWithConstraints].
 */
@Composable
private fun DamageBubbleOverlay(bubble: DamageBubble, tileSize: Dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(bubble.id) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(BUBBLE_LIFETIME_MS, easing = LinearEasing),
        )
    }
    val progress = anim.value

    // Start anchored near the top of the piece's tile, rise by ~80 % of a tile.
    val baseTopOffsetDp = tileSize * bubble.row + tileSize * 0.05f
    val rise = tileSize * 0.8f * progress

    Text(
        text = "-${bubble.amount}",
        color = DAMAGE_BUBBLE_COLOR,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(tileSize)
            .offset(
                x = tileSize * bubble.column,
                y = baseTopOffsetDp - rise,
            )
            .alpha(1f - progress),
    )
}
