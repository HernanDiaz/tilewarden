package com.tilewarden.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.tilewarden.core.Axis
import com.tilewarden.core.XYLocation
import kotlinx.coroutines.delay
import kotlin.math.abs
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
    obstacles: List<ObstacleRender> = emptyList(),
    pits: List<XYLocation> = emptyList(),
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
    wardenMode: Boolean = false,
    onWardenSlide: (axis: Axis, index: Int, delta: Int) -> Unit = { _, _, _ -> },
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

    // A single floor tile used everywhere in the playable area.
    val floorTile = ImageBitmap.imageResource(R.drawable.floor_1)
    // Dungeon furniture: open pits (16x16 tile) and crates (16x24,
    // anchored to the cell bottom like the humanoid sprites).
    val pitTile   = ImageBitmap.imageResource(R.drawable.pit_hole)
    val crateTile = ImageBitmap.imageResource(R.drawable.obstacle_crate)
    // Only two wall tiles are used now: the dark-strip remate at the top
    // of every wall and the plain brick body underneath. No sides, no
    // corners — the room is open on the left and right.
    val wallTopMid = ImageBitmap.imageResource(R.drawable.wall_top_mid)
    val wallMid    = ImageBitmap.imageResource(R.drawable.wall_mid)

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

    // Crates animate their ride along a slide just like characters do.
    val animatedObstacles: List<AnimatedObstacle> = obstacles.map { o ->
        key(o.name) {
            val animCol by animateFloatAsState(
                targetValue = o.column.toFloat(),
                animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                label = "ocol_${o.name}",
            )
            val animRow by animateFloatAsState(
                targetValue = o.row.toFloat(),
                animationSpec = tween(MOVE_ANIMATION_MS, easing = FastOutSlowInEasing),
                label = "orow_${o.name}",
            )
            AnimatedObstacle(animCol, animRow)
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

    // The whole canvas is pulled UP by this many tile-heights so the
    // empty space above the top wall disappears. The Box's vertical size
    // is reduced by the same amount so the bottom edge stays put.
    // 1.0f = shift up by one full row.
    val transparentBandFraction = 1f

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
            }
            .pointerInput(wardenMode, rows, columns) {
                // Warden Action gesture: while warden mode is on, dragging
                // horizontally on a row slides that row, dragging vertically
                // on a column slides that column. One slide per gesture.
                if (!wardenMode) return@pointerInput
                var startRow = -1
                var startCol = -1
                var accX = 0f
                var accY = 0f
                var fired = false
                detectDragGestures(
                    onDragStart = { offset ->
                        val tileSizePx = size.width.toFloat() / renderCols
                        val shift = tileSizePx * transparentBandFraction
                        startCol = (offset.x / tileSizePx).toInt() - playColOffset
                        startRow = ((offset.y + shift) / tileSizePx).toInt() - playRowOffset
                        accX = 0f; accY = 0f; fired = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (fired) return@detectDragGestures
                        accX += dragAmount.x
                        accY += dragAmount.y
                        val threshold = size.width.toFloat() / renderCols * 0.5f
                        if (abs(accX) >= threshold || abs(accY) >= threshold) {
                            val horizontal = abs(accX) >= abs(accY)
                            if (horizontal && startRow in 0 until rows) {
                                onWardenSlide(Axis.ROW, startRow, if (accX > 0) 1 else -1)
                                fired = true
                            } else if (!horizontal && startCol in 0 until columns) {
                                onWardenSlide(Axis.COLUMN, startCol, if (accY > 0) 1 else -1)
                                fired = true
                            }
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
            val pieceRadius       = tileSizePx * 0.36f

            translate(top = -canvasShift) {

            // 1) Floor — same tile in every playable cell.
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    drawTile(
                        bitmap = floorTile,
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

            // 2.5) Open pits — holes in the world, drawn over the floor.
            // Pits never move; only pieces ride slides.
            for (pit in pits) {
                drawTile(
                    bitmap = pitTile,
                    renderRow = pit.x + playRowOffset,
                    renderCol = pit.y + playColOffset,
                    tileSizePx = tileSizePx,
                )
            }

            // 2.7) Crates: bottom-anchored sprites that slide with their
            // line. Drawn before characters so heroes pass in front.
            for (ao in animatedObstacles) {
                val spriteScale = tileSizePx * 0.95f / crateTile.width.toFloat()
                val scaledW = crateTile.width  * spriteScale
                val scaledH = crateTile.height * spriteScale
                val ocx = (ao.column + playColOffset) * tileSizePx + tileSizePx / 2f
                val cellBottom = (ao.row + playRowOffset) * tileSizePx + tileSizePx
                drawImage(
                    image = crateTile,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(crateTile.width, crateTile.height),
                    dstOffset = IntOffset(
                        (ocx - scaledW / 2f).roundToInt(),
                        (cellBottom - scaledH).roundToInt(),
                    ),
                    dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
                    filterQuality = FilterQuality.None,
                )
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

            // 3.5) Selected hero cell highlight — drawn UNDER the sprite so
            // the 16x28 sprite poking out the top of its tile isn't masked
            // by the yellow frame. Thin, translucent border plus a very
            // soft fill so the cell still reads as 'picked' even when the
            // sprite's head covers the top edge of the border.
            selectedHero?.let { sel ->
                pieces.firstOrNull { it.name == sel }?.let { selPiece ->
                    val sx = (selPiece.column + playColOffset) * tileSizePx
                    val sy = (selPiece.row + playRowOffset) * tileSizePx
                    drawRect(
                        color = SELECTION_BORDER_COLOR.copy(alpha = 0.16f),
                        topLeft = Offset(sx, sy),
                        size = Size(tileSizePx, tileSizePx),
                    )
                    val w = borderPx * 1.5f
                    drawRect(
                        color = SELECTION_BORDER_COLOR.copy(alpha = 0.7f),
                        topLeft = Offset(sx + w / 2f, sy + w / 2f),
                        size = Size(tileSizePx - w, tileSizePx - w),
                        style = Stroke(width = w),
                    )
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

                // 5) Attack flash frame around the attacker's cell. Drawn
                // OVER the sprite (unlike the selection ring) because it's
                // a brief impact effect that should pop visually.
                if (piece.name in attackingPieces) {
                    drawRect(
                        color = ATTACK_BORDER_COLOR.copy(alpha = alpha),
                        topLeft = Offset(
                            (piece.column + playColOffset) * tileSizePx + attackBorderPx / 2f,
                            (piece.row + playRowOffset) * tileSizePx + attackBorderPx / 2f,
                        ),
                        size = Size(
                            tileSizePx - attackBorderPx,
                            tileSizePx - attackBorderPx,
                        ),
                        style = Stroke(width = attackBorderPx),
                    )
                }
            }

            // 6) Warden mode frame: a gold border around the playable area
            // signals that drags will slide tile lines instead of selecting.
            if (wardenMode) {
                val w = borderPx * 2.5f
                drawRect(
                    color = SELECTION_BORDER_COLOR.copy(alpha = 0.85f),
                    topLeft = Offset(
                        playColOffset * tileSizePx + w / 2f,
                        playRowOffset * tileSizePx + w / 2f,
                    ),
                    size = Size(
                        columns * tileSizePx - w,
                        rows * tileSizePx - w,
                    ),
                    style = Stroke(width = w),
                )
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

private data class AnimatedObstacle(
    val column: Float,
    val row: Float,
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
