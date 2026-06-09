package com.tilewarden.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.launch

@Composable
fun GameScreen(session: GameSession) {
    var autoPlay by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Reading these on the GameScreen scope establishes Compose-state
    // subscriptions, which guarantees recomposition when the session ticks.
    val round       = session.round
    val isOver      = session.isOver
    val winner      = session.winner
    val isAnimating = session.isAnimating

    // Auto-play: as long as the toggle is on and the game hasn't ended,
    // chain nextRound() calls. Each call is suspending and only returns
    // when the round's events finish replaying, so we don't need extra
    // delays here — the visual cadence is set by the per-event timings.
    LaunchedEffect(autoPlay) {
        while (autoPlay && !session.isOver) {
            session.nextRound()
        }
        if (session.isOver) autoPlay = false
    }

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

        PanelCard {
            BoardCanvas(
                rows = session.boardRows,
                columns = session.boardColumns,
                pieces = session.pieces,
                dyingPieces = session.dyingPieces,
                attackingPieces = session.attackingPieces,
                damageBubbles = session.damageBubbles,
            )
        }

        HudPanel(pieces = session.pieces)

        EventLogPanel(
            log = session.log,
            modifier = Modifier.weight(1f, fill = true),
        )

        Controls(
            autoPlay = autoPlay,
            isOver = isOver,
            isAnimating = isAnimating,
            onNext = { scope.launch { session.nextRound() } },
            onToggleAuto = { autoPlay = !autoPlay },
            onReset = {
                autoPlay = false
                session.reset()
            },
        )
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
    autoPlay: Boolean,
    isOver: Boolean,
    isAnimating: Boolean,
    onNext: () -> Unit,
    onToggleAuto: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onNext,
            // Disable while the previous round's replay is still in flight,
            // and obviously when the game is over or auto-play is running.
            enabled = !isOver && !autoPlay && !isAnimating,
            modifier = Modifier.weight(1f),
        ) {
            Text("Next round")
        }
        Button(
            onClick = onToggleAuto,
            enabled = !isOver,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (autoPlay) "Pause" else "Auto-play")
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f),
        ) {
            Text("Reset")
        }
    }
}
