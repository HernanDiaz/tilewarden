package com.tilewarden.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.tilewarden.core.XYLocation

private const val MOVE_ANIMATION_MS = 400
private val ATTACK_BORDER_COLOR    = Color(0xFFE25D4A)              // terracotta
private val DAMAGE_BUBBLE_COLOR    = Color(0xFFE25D4A)              // terracotta
private val SELECTION_BORDER_COLOR = Color(0xFFF0C969)              // gold bright
private val VALID_MOVE_COLOR       = Color(0x667A9F5A)              // moss green tint
private val VALID_ATTACK_COLOR     = Color(0x66E25D4A)              // terracotta tint

/**
 * 2D board with full interactivity: tap detection, selection highlight,
 * valid-move and valid-attack overlays, "already acted" fading, plus the
 * VFX from milestone 4 (movement slide, attack flash, death fade,
 * floating damage labels).
 */
@Composable
fun BoardCanvas(
    rows: Int,
    columns: Int,
    pieces: List<PieceRender>,
    dyingPieces: List<String>,
    attackingPieces: List<String>,
    damageBubbles: List<DamageBubble>,
    selectedHero: String?,
    validMoves: Set<XYLocation>,
    validAttackTargets: Set<String>,
    actedHeroes: List<String>,
    onTileTap: (row: Int, column: Int) -> Unit,
    onTileLongPress: (row: Int, column: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val tileFill   = MaterialTheme.colorScheme.surface
    val tileBorder = MaterialTheme.colorScheme.outline
    val emptyDot   = MaterialTheme.colorScheme.surfaceVariant
    val symbolInk  = Color(0xFF2A1A14)   // same as BgDeep; black-on-piece reads warmly

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 16.sp,
        color = symbolInk,
        fontWeight = FontWeight.Bold,
    )

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
            val deathAlpha by animateFloatAsState(
                targetValue = if (piece.name in dyingPieces) 0f else 1f,
                animationSpec = tween(DEATH_FADE_MS),
                label = "alpha_${piece.name}",
            )
            AnimatedPiece(piece, animCol, animRow, deathAlpha)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / rows.toFloat())
            .pointerInput(rows, columns) {
                detectTapGestures(
                    onTap = { offset ->
                        val tileSizePx = size.width.toFloat() / columns
                        val col = (offset.x / tileSizePx).toInt().coerceIn(0, columns - 1)
                        val row = (offset.y / tileSizePx).toInt().coerceIn(0, rows - 1)
                        onTileTap(row, col)
                    },
                    onLongPress = { offset ->
                        val tileSizePx = size.width.toFloat() / columns
                        val col = (offset.x / tileSizePx).toInt().coerceIn(0, columns - 1)
                        val row = (offset.y / tileSizePx).toInt().coerceIn(0, rows - 1)
                        onTileLongPress(row, col)
                    },
                )
            },
    ) {
        val tileSize: Dp = maxWidth / columns

        Canvas(modifier = Modifier.fillMaxSize()) {
            val tileSizePx = size.width / columns
            val borderPx          = (tileSizePx * 0.04f).coerceAtLeast(1f)
            val attackBorderPx    = borderPx * 2.5f
            val selectionBorderPx = borderPx * 3f
            val pieceRadius       = tileSizePx * 0.36f
            val emptyDotRadius    = tileSizePx * 0.06f

            // Grid + valid-move highlights.
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val topLeft = Offset(col * tileSizePx, row * tileSizePx)
                    val cellSize = Size(tileSizePx, tileSizePx)
                    drawRect(color = tileFill, topLeft = topLeft, size = cellSize)

                    if (XYLocation(row, col) in validMoves) {
                        drawRect(color = VALID_MOVE_COLOR, topLeft = topLeft, size = cellSize)
                    }

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
                val acted = piece.name in actedHeroes
                val alpha = ap.alpha * (if (acted) ACTED_ALPHA else 1f)
                val cx = ap.column * tileSizePx + tileSizePx / 2f
                val cy = ap.row    * tileSizePx + tileSizePx / 2f

                // Valid-attack tint behind the disc.
                if (piece.name in validAttackTargets) {
                    drawRect(
                        color = VALID_ATTACK_COLOR,
                        topLeft = Offset(
                            piece.column * tileSizePx,
                            piece.row * tileSizePx,
                        ),
                        size = Size(tileSizePx, tileSizePx),
                    )
                }

                drawCircle(
                    color = piece.color.copy(alpha = alpha),
                    radius = pieceRadius,
                    center = Offset(cx, cy),
                )

                val isAttacking = piece.name in attackingPieces
                val isSelected  = piece.name == selectedHero
                val outlineColor: Color
                val outlineWidth: Float
                when {
                    isSelected  -> { outlineColor = SELECTION_BORDER_COLOR; outlineWidth = selectionBorderPx }
                    isAttacking -> { outlineColor = ATTACK_BORDER_COLOR;    outlineWidth = attackBorderPx }
                    else        -> { outlineColor = symbolInk;              outlineWidth = borderPx }
                }
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

        for (bubble in damageBubbles) {
            key(bubble.id) {
                DamageBubbleOverlay(bubble = bubble, tileSize = tileSize)
            }
        }
    }
}

private data class AnimatedPiece(
    val piece: PieceRender,
    val column: Float,
    val row: Float,
    val alpha: Float,
)

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
