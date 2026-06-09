package com.tilewarden.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tilewarden.core.Side
import com.tilewarden.core.XYLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    session: GameSession,
    onBackToMenu: () -> Unit = {},
) {
    var inspected: PieceRender? by remember { mutableStateOf(null) }
    var showSummary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val round         = session.round
    val isOver        = session.isOver
    val winner        = session.winner
    val isAnimating   = session.isAnimating
    val selectedHero  = session.selectedHero
    val validMoves    = session.validMoveTargets()
    val attackTargets = session.validAttackTargets()

    // Keep the inspector in sync: if the inspected piece dies / leaves the
    // board, drop the overlay automatically.
    LaunchedEffect(session.pieces.size, isAnimating) {
        inspected?.let { snap ->
            val current = session.pieces.firstOrNull { it.name == snap.name }
            inspected = current  // null if it died; refreshed values otherwise
        }
    }

    // Auto-pop the summary modal once the game ends. Small delay so the last
    // replay frame is visible before the overlay covers it.
    LaunchedEffect(isOver) {
        if (isOver) {
            delay(500)
            showSummary = true
        } else {
            showSummary = false
        }
    }

    // Auto-advance once every alive hero has fully spent their turn. Manual
    // play turns into something like: tap heroes, move them, see the AI
    // round resolve, repeat.
    //
    // nextRound() is dispatched into `scope` (the screen-level coroutine
    // scope), NOT awaited inside the LaunchedEffect. If we awaited it here
    // the effect would cancel mid-call as soon as nextRound mutated any of
    // the keys, interrupting resolveRound + replayBuffered halfway through.
    LaunchedEffect(
        session.actedThisRound.size,
        session.pieces.size,
        session.isOver,
    ) {
        if (session.isAnimating || session.isOver) return@LaunchedEffect
        val aliveHeroes = session.pieces.filter { it.isHero }
        if (aliveHeroes.isEmpty()) return@LaunchedEffect
        if (aliveHeroes.all { it.name in session.actedThisRound }) {
            delay(600)
            if (!session.isAnimating && !session.isOver) {
                scope.launch { session.nextRound() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                round = round,
                totalRounds = session.totalRounds,
                heroesAlive = session.heroesAlive,
                monstersAlive = session.monstersAlive,
                isOver = isOver,
                winner = winner,
            )

            ScoreBoard(
                pieces = session.pieces,
                initialHeroBody = session.initialHeroBody,
                initialMonsterBody = session.initialMonsterBody,
            )

            PanelCard {
                BoardCanvas(
                    rows = session.boardRows,
                    columns = session.boardColumns,
                    pieces = session.pieces,
                    dyingPieces = session.dyingPieces,
                    attackingPieces = session.attackingPieces,
                    damageBubbles = session.damageBubbles,
                    selectedHero = selectedHero,
                    validMoves = validMoves,
                    validAttackTargets = attackTargets,
                    actedHeroes = session.actedThisRound,
                    facingLeft = session.facingLeft,
                    onTileTap = { row, col ->
                        handleTap(session, row, col, scope)
                    },
                    onTileLongPress = { row, col ->
                        inspected = session.pieces.firstOrNull {
                            it.row == row && it.column == col
                        }
                    },
                )
            }

            EventLogPanel(
                log = session.log,
                modifier = Modifier.weight(1f, fill = true),
            )

            Controls(
                isOver = isOver,
                onReset = { session.reset() },
                onMenu = onBackToMenu,
                showSummaryButton = isOver && !showSummary,
                onShowSummary = { showSummary = true },
            )
        }

        inspected?.let { piece ->
            PieceInspector(
                piece = piece,
                onDismiss = { inspected = null },
            )
        }

        if (showSummary && isOver) {
            EndOfGameSummary(
                session = session,
                showConfetti = winner == com.tilewarden.core.Side.HEROES,
                onRematch = {
                    showSummary = false
                    session.reset()
                },
                onNewGame = {
                    showSummary = false
                    onBackToMenu()
                },
                onClose = { showSummary = false },
            )
        }
    }
}

/**
 * Tap dispatcher: figure out what the tap means given current selection
 * state, and call the right session method.
 */
private fun handleTap(
    session: GameSession,
    row: Int,
    column: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (session.isAnimating || session.isOver) return

    val tile = XYLocation(row, column)
    val pieceHere = session.pieces.firstOrNull { it.row == row && it.column == column }

    when {
        // Tapped a hero: select or toggle selection.
        pieceHere != null && pieceHere.isHero -> {
            if (session.selectedHero == pieceHere.name) {
                session.selectHero(null)
            } else {
                session.selectHero(pieceHere.name)
            }
        }
        // Tapped an enemy with a hero selected: attack if it's a valid target.
        pieceHere != null && !pieceHere.isHero -> {
            if (pieceHere.name in session.validAttackTargets()) {
                scope.launch { session.manualAttack(pieceHere.name) }
            }
        }
        // Tapped empty square with a hero selected: try to move there.
        pieceHere == null && session.selectedHero != null -> {
            if (tile in session.validMoveTargets()) {
                session.manualMove(tile)
            } else {
                // Tap outside legal moves: deselect.
                session.selectHero(null)
            }
        }
    }
}

@Composable
private fun Header(
    round: Int,
    totalRounds: Int,
    heroesAlive: Int,
    monstersAlive: Int,
    isOver: Boolean,
    winner: Side?,
) {
    Column {
        Text(
            text = "Tilewarden",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        val subtitle = if (isOver) {
            val verdict = when (winner) {
                Side.HEROES   -> "Heroes win"
                Side.MONSTERS -> "Monsters win"
                Side.DRAW     -> "Draw"
                null          -> "Finished"
            }
            "Game over — $verdict"
        } else {
            "Round $round / $totalRounds  ·  Heroes $heroesAlive  ·  Monsters $monstersAlive"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EventLogPanel(
    log: List<String>,
    modifier: Modifier = Modifier,
) {
    val visible = remember(log.size) { log.filter { it.isNotBlank() } }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.lastIndex)
    }
    PanelCard(modifier = modifier) {
        if (visible.isEmpty()) {
            Text(
                text = "(no events yet)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(visible) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun Controls(
    isOver: Boolean,
    onReset: () -> Unit,
    onMenu: () -> Unit,
    showSummaryButton: Boolean,
    onShowSummary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSummaryButton) {
            Button(
                onClick = onShowSummary,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Show summary") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset")
            }
            OutlinedButton(
                onClick = onMenu,
                modifier = Modifier.weight(1f),
            ) {
                Text("Menu")
            }
        }
    }
}
