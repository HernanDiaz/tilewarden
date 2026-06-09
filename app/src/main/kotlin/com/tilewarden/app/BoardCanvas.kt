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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.tilewarden.core.XYLocation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MOVE_ANIMATION_MS = 400
private const val IDLE_FRAME_MS = 200L      // ~5 fps idle animation
private const val IDLE_FRAME_COUNT = 4

private val ATTACK_BORDER_COLOR    = Color(0xFFFF5040)
private val DAMAGE_BUBBLE_COLOR    = Color(0xFFE04A4A)
private val SELECTION_BORDER_COLOR = Color(0xFFFFDD66)
private val VALID_MOVE_COLOR       = Color(0x6685D67A)
private val VALID_ATTACK_COLOR     = Color(0x66FF6E4A)

/**
 * 2D board with full interactivity, animated movement, attack flash,
 * death fade, valid-move/valid-attack highlights and floating damage labels.
 *
 * Pieces use sprites from 0x72's DungeonTilesetII (CC-BY 4.0). Each
 * character has four idle frames (`sprite_<role>_f0..f3.png`) that the
 * Canvas cycles through every [IDLE_FRAME_MS] ms so the board feels alive.
 *
 * Sprite dimensions vary (16x16 for goblin/skelet, 16x28 for knight/dwarf).
 * The renderer preserves each sprite's aspect ratio and anchors it by the
 * BOTTOM of the cell, so tall humanoids correctly poke out above their
 * tile — matching the convention in classic pixel-art roguelikes.
 *
 * Crisp pixels: [FilterQuality.None] keeps the rescale nearest-neighbour
 * (no Compose default Linear blur).
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
    facingLeft: Map<String, Boolean>,
    onTileTap: (row: Int, column: Int) -> Unit,
    onTileLongPress: (row: Int, column: Int) -> Unit = { _, _ -> },
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

    // Pre-load all 16 idle frames (4 per character class).
    // Loading is composable-scoped, the bitmaps are cached by Compose.
    val barbarianFrames = listOf(
        ImageBitmap.imageResource(R.drawable.sprite_barbarian_f0),
        ImageBitmap.imageResource(R.drawable.sprite_barbarian_f1),
        ImageBitmap.imageResource(R.drawable.sprite_barbarian_f2),
        ImageBitmap.imageResource(R.drawable.sprite_barbarian_f3),
    )
    val dwarfFrames = listOf(
        ImageBitmap.imageResource(R.drawable.sprite_dwarf_f0),
        ImageBitmap.imageResource(R.drawable.sprite_dwarf_f1),
        ImageBitmap.imageResource(R.drawable.sprite_dwarf_f2),
        ImageBitmap.imageResource(R.drawable.sprite_dwarf_f3),
    )
    val goblinFrames = listOf(
        ImageBitmap.imageResource(R.drawable.sprite_goblin_f0),
        ImageBitmap.imageResource(R.drawable.sprite_goblin_f1),
        ImageBitmap.imageResource(R.drawable.sprite_goblin_f2),
        ImageBitmap.imageResource(R.drawable.sprite_goblin_f3),
    )
    val mummyFrames = listOf(
        ImageBitmap.imageResource(R.drawable.sprite_mummy_f0),
        ImageBitmap.imageResource(R.drawable.sprite_mummy_f1),
        ImageBitmap.imageResource(R.drawable.sprite_mummy_f2),
        ImageBitmap.imageResource(R.drawable.sprite_mummy_f3),
    )

    fun framesFor(symbol: Char): List<ImageBitmap>? = when (symbol) {
        'B' -> barbarianFrames
        'D' -> dwarfFrames
        'G' -> goblinFrames
        'M' -> mummyFrames
        else -> null
    }

    // Global idle frame cycler — every piece advances in sync.
    var idleFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(IDLE_FRAME_MS)
            idleFrame = (idleFrame + 1) % IDLE_FRAME_COUNT
        }
    }

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

            for (ap in animatedPieces) {
                val piece = ap.piece
                val acted = piece.name in actedHeroes
                val alpha = ap.alpha * (if (acted) ACTED_ALPHA else 1f)
                val cx = ap.column * tileSizePx + tileSizePx / 2f

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

                val frames = framesFor(piece.symbol)
                if (frames != null) {
                    val bitmap = frames[idleFrame]
                    // Scale by WIDTH to fit the cell (~95%), then preserve
                    // aspect ratio so tall sprites stay tall. Anchor the
                    // bottom of the sprite to the bottom of the cell so
                    // 16x28 humanoids poke out above their tile naturally.
                    val spriteScale = tileSizePx * 0.95f / bitmap.width.toFloat()
                    val scaledW = bitmap.width  * spriteScale
                    val scaledH = bitmap.height * spriteScale
                    val cellBottom = ap.row * tileSizePx + tileSizePx
                    val left = cx - scaledW / 2f
                    val top  = cellBottom - scaledH

                    val flipX = facingLeft[piece.name] == true

                    if (flipX) {
                        // Mirror around the sprite's own centre so it stays
                        // in the same cell — just facing left.
                        scale(
                            scaleX = -1f,
                            scaleY = 1f,
                            pivot = Offset(cx, top + scaledH / 2f),
                        ) {
                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bitmap.width, bitmap.height),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
                                alpha = alpha,
                                filterQuality = FilterQuality.None,
                            )
                        }
                    } else {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
                            alpha = alpha,
                            filterQuality = FilterQuality.None,
                        )
                    }
                } else {
                    // Fallback for any future character class without a sprite.
                    val cy = ap.row * tileSizePx + tileSizePx / 2f
                    drawCircle(
                        color = piece.color.copy(alpha = alpha),
                        radius = pieceRadius,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = symbolInk.copy(alpha = alpha),
                        radius = pieceRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = borderPx),
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

                val isAttacking = piece.name in attackingPieces
                val isSelected  = piece.name == selectedHero
                if (isSelected || isAttacking) {
                    val outlineColor = if (isSelected) SELECTION_BORDER_COLOR
                                       else            ATTACK_BORDER_COLOR
                    val outlineWidth = if (isSelected) selectionBorderPx
                                       else            attackBorderPx
                    drawRect(
                        color = outlineColor.copy(alpha = alpha),
                        topLeft = Offset(
                            piece.column * tileSizePx + outlineWidth / 2f,
                            piece.row * tileSizePx + outlineWidth / 2f,
                        ),
                        size = Size(
                            tileSizePx - outlineWidth,
                            tileSizePx - outlineWidth,
                        ),
                        style = Stroke(width = outlineWidth),
                    )
                }
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
