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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
private const val IDLE_FRAME_MS = 200L
private const val IDLE_FRAME_COUNT = 4

private val ATTACK_BORDER_COLOR    = Color(0xFFFF5040)
private val DAMAGE_BUBBLE_COLOR    = Color(0xFFE04A4A)
private val SELECTION_BORDER_COLOR = Color(0xFFFFDD66)
private val VALID_MOVE_COLOR       = Color(0x6685D67A)
private val VALID_ATTACK_COLOR     = Color(0x66FF6E4A)

/**
 * 2D board with pixel-art floor + wall perimeter, animated character
 * sprites, tap / long-press detection and combat VFX.
 *
 * The render grid is two cells wider and four cells taller than the
 * playable area. Horizontal walls are two tiles high — a row of
 * `wall_top_mid` (the dark remate strip) over a row of `wall_mid`
 * (the plain brick body) — both at the top and the bottom. Vertical
 * sides are one column wide with `wall_left` / `wall_right`. The
 * playable area is offset by (+1 column, +2 rows).
 *
 * Floor tiles are picked from four variants using a deterministic hash
 * of (row, column) so the same square always shows the same texture
 * between runs and between rebuilds of the snapshot.
 *
 * All sprites are 16×16 except the humanoids (16×28); the renderer
 * preserves each one's aspect ratio. [FilterQuality.None] keeps the
 * nearest-neighbour scaling crisp.
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
    val symbolInk  = Color(0xFF1B1714)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 16.sp,
        color = symbolInk,
        fontWeight = FontWeight.Bold,
    )

    // Character idle frames (4 per class, looped at 5 fps).
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

    // Floor + wall tiles.
    val floorTiles = listOf(
        ImageBitmap.imageResource(R.drawable.floor_1),
        ImageBitmap.imageResource(R.drawable.floor_2),
        ImageBitmap.imageResource(R.drawable.floor_3),
        ImageBitmap.imageResource(R.drawable.floor_4),
    )
    // Only two wall tiles are used now: the dark-strip remate at the top
    // of every wall and the plain brick body underneath. No sides, no
    // corners — the room is open on the left and right.
    val wallTopMid = ImageBitmap.imageResource(R.drawable.wall_top_mid)
    val wallMid    = ImageBitmap.imageResource(R.drawable.wall_mid)

    fun floorAt(r: Int, c: Int): ImageBitmap {
        // Deterministic hash → stable floor per cell across recompositions.
        val h = (r * 31 + c * 17) and Int.MAX_VALUE
        return floorTiles[h % floorTiles.size]
    }

    // Global idle frame cycler.
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

    // Render grid: same width as the playable area (no left/right walls).
    // Top wall is 2 rows (remate + body). Bottom wall is 2 rows too, but
    // its remate row overlaps the last floor row — wall_top_mid's top
    // band is transparent so the floor shows through, and the dark
    // remate line ends up flush against the wall body. Total perimeter
    // height is therefore 3 rows, not 4.
    val renderCols = columns
    val renderRows = rows + 3
    val playRowOffset = 2  // top wall = 2 rows
    val playColOffset = 0  // no side walls

    // wall_top_mid's top 6 of 16 rows are transparent. To stop that band
    // from showing as a black strip above the top remate, we shift the
    // whole canvas content up by that amount and crop the Box vertically
    // by the same amount.
    val transparentBandFraction = 6f / 16f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(renderCols.toFloat() / (renderRows.toFloat() - transparentBandFraction))
            .pointerInput(rows, columns) {
                detectTapGestures(
                    onTap = { offset ->
                        val tileSizePx = size.width.toFloat() / renderCols
                        // The Box is shorter than renderRows * tileSizePx by
                        // transparentBandFraction. The drawing is shifted up
                        // by the same amount, so add it back to map taps to
                        // logical render rows.
                        val shift = tileSizePx * transparentBandFraction
                        val rc = (offset.x / tileSizePx).toInt()
                        val rr = ((offset.y + shift) / tileSizePx).toInt()
                        val playCol = rc - playColOffset
                        val playRow = rr - playRowOffset
                        if (playRow in 0 until rows && playCol in 0 until columns) {
                            onTileTap(playRow, playCol)
                        }
                    },
                    onLongPress = { offset ->
                        val tileSizePx = size.width.toFloat() / renderCols
                        val shift = tileSizePx * transparentBandFraction
                        val rc = (offset.x / tileSizePx).toInt()
                        val rr = ((offset.y + shift) / tileSizePx).toInt()
                        val playCol = rc - playColOffset
                        val playRow = rr - playRowOffset
                        if (playRow in 0 until rows && playCol in 0 until columns) {
                            onTileLongPress(playRow, playCol)
                        }
                    },
                )
            },
    ) {
        val tileSize: Dp = maxWidth / renderCols

        Canvas(modifier = Modifier.fillMaxSize()) {
            val tileSizePx = size.width / renderCols
            val canvasShift = tileSizePx * transparentBandFraction
            val borderPx          = (tileSizePx * 0.04f).coerceAtLeast(1f)
            val attackBorderPx    = borderPx * 2.5f
            val selectionBorderPx = borderPx * 3f
            val pieceRadius       = tileSizePx * 0.36f

            translate(top = -canvasShift) {

            // 1) Floor — every playable cell gets a floor tile based on its hash.
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    drawTile(
                        bitmap = floorAt(r, c),
                        renderRow = r + playRowOffset,
                        renderCol = c + playColOffset,
                        tileSizePx = tileSizePx,
                    )
                }
            }

            // 2) Horizontal walls only — nothing to the left or right.
            //
            // wall_top_mid has 6 fully transparent rows at the top of
            // its sprite. For the BOTTOM wall we exploit that: the
            // remate is drawn ON TOP of the last floor row, so the
            // transparent band reveals the floor and the dark line
            // sits flush against the wall body below.
            //
            // Row 0:        wall_top_mid x cols  (top remate)
            // Row 1:        wall_mid     x cols  (top body)
            // Rows 2..N+1:  playable floor
            //   - Row N+1 (= renderRows - 2) is BOTH the last floor row
            //     AND the bottom remate row. The wall sprite is drawn
            //     after the floor so it overlays.
            // Row N+2:      wall_mid     x cols  (bottom body)
            for (rc in 0 until renderCols) {
                drawTile(wallTopMid, renderRow = 0,              renderCol = rc, tileSizePx = tileSizePx)
                drawTile(wallMid,    renderRow = 1,              renderCol = rc, tileSizePx = tileSizePx)
                // Bottom remate overlays the last floor row.
                drawTile(wallTopMid, renderRow = renderRows - 2, renderCol = rc, tileSizePx = tileSizePx)
                drawTile(wallMid,    renderRow = renderRows - 1, renderCol = rc, tileSizePx = tileSizePx)
            }

            // 3) Valid-move tints over playable floor cells.
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    if (XYLocation(r, c) in validMoves) {
                        drawRect(
                            color = VALID_MOVE_COLOR,
                            topLeft = Offset((c + playColOffset) * tileSizePx, (r + playRowOffset) * tileSizePx),
                            size = Size(tileSizePx, tileSizePx),
                        )
                    }
                }
            }

            // 4) Pieces (with cell offset of +1 in both axes).
            for (ap in animatedPieces) {
                val piece = ap.piece
                val acted = piece.name in actedHeroes
                val alpha = ap.alpha * (if (acted) ACTED_ALPHA else 1f)
                val cx = (ap.column + playColOffset) * tileSizePx + tileSizePx / 2f

                if (piece.name in validAttackTargets) {
                    drawRect(
                        color = VALID_ATTACK_COLOR,
                        topLeft = Offset(
                            (piece.column + playColOffset) * tileSizePx,
                            (piece.row + playRowOffset) * tileSizePx,
                        ),
                        size = Size(tileSizePx, tileSizePx),
                    )
                }

                val frames = framesFor(piece.symbol)
                if (frames != null) {
                    val bitmap = frames[idleFrame]
                    val spriteScale = tileSizePx * 0.95f / bitmap.width.toFloat()
                    val scaledW = bitmap.width  * spriteScale
                    val scaledH = bitmap.height * spriteScale
                    val cellBottom = (ap.row + playRowOffset) * tileSizePx + tileSizePx
                    val left = cx - scaledW / 2f
                    val top  = cellBottom - scaledH

                    val flipX = facingLeft[piece.name] == true
                    if (flipX) {
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
                    val cy = (ap.row + playRowOffset) * tileSizePx + tileSizePx / 2f
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

                // 5) Selection / attack frames around the piece's cell.
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
                            (piece.column + playColOffset) * tileSizePx + outlineWidth / 2f,
                            (piece.row + playRowOffset) * tileSizePx + outlineWidth / 2f,
                        ),
                        size = Size(
                            tileSizePx - outlineWidth,
                            tileSizePx - outlineWidth,
                        ),
                        style = Stroke(width = outlineWidth),
                    )
                }
            }
            } // close translate(top = -canvasShift)
        }

        // Damage bubbles — positioned in playable coordinates, with the
        // same vertical shift applied so they line up with the on-canvas
        // sprites.
        for (bubble in damageBubbles) {
            key(bubble.id) {
                DamageBubbleOverlay(
                    bubble = bubble,
                    tileSize = tileSize,
                    yShift = tileSize * transparentBandFraction,
                )
            }
        }
    }
}

/** Helper: draw a single tile-sized bitmap at (renderRow, renderCol). */
private fun DrawScope.drawTile(
    bitmap: ImageBitmap,
    renderRow: Int,
    renderCol: Int,
    tileSizePx: Float,
) {
    val left = renderCol * tileSizePx
    val top  = renderRow * tileSizePx
    val sizeInt = tileSizePx.roundToInt()
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(sizeInt, sizeInt),
        filterQuality = FilterQuality.None,
    )
}

private data class AnimatedPiece(
    val piece: PieceRender,
    val column: Float,
    val row: Float,
    val alpha: Float,
)

@Composable
private fun DamageBubbleOverlay(
    bubble: DamageBubble,
    tileSize: Dp,
    yShift: Dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(bubble.id) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(BUBBLE_LIFETIME_MS, easing = LinearEasing),
        )
    }
    val progress = anim.value
    // +2 row offset for the top wall; minus yShift to match the canvas's
    // upward translation.
    val baseTopOffsetDp = tileSize * (bubble.row + 2) + tileSize * 0.05f - yShift
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
                x = tileSize * bubble.column,  // playColOffset = 0 — no side walls
                y = baseTopOffsetDp - rise,
            )
            .alpha(1f - progress),
    )
}
