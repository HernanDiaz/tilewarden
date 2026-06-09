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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tilewarden.core.Side
import kotlinx.coroutines.delay

private const val AUTO_PLAY_DELAY_MS = 600L

@Composable
fun GameScreen(session: GameSession) {
    var autoPlay by remember { mutableStateOf(false) }

    // ---- Subscribe to every observable on the session up front. ----
    // Reading them HERE establishes the Compose-state subscription on the
    // GameScreen scope, which guarantees that any tick of the engine
    // recomposes the whole screen — and therefore recomputes the
    // remembered snapshots below.
    val round         = session.round
    val isOver        = session.isOver
    val winner        = session.winner
    val totalRounds   = session.totalRounds

    // Snapshots — re-derived only when the round advances. Their data-class
    // equality means Compose will skip work if nothing actually changed.
    val boardSnapshot = remember(round) { BoardSnapshot.from(session.game) }
    val charactersText = remember(round) { session.charactersText }

    // Auto-play coroutine: while autoPlay is true and the game isn't over,
    // step one round every AUTO_PLAY_DELAY_MS.
    LaunchedEffect(autoPlay, isOver) {
        if (autoPlay && !isOver) {
            while (autoPlay && !isOver) {
                session.nextRound()
                delay(AUTO_PLAY_DELAY_MS)
            }
            autoPlay = false
        }
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
            totalRounds = totalRounds,
            heroesAlive = boardSnapshot.heroesAlive,
            monstersAlive = boardSnapshot.monstersAlive,
            isOver = isOver,
            winner = winner,
        )

        PanelCard {
            BoardCanvas(snapshot = boardSnapshot)
        }

        CharactersPanel(text = charactersText)

        EventLogPanel(
            log = session.log,
            modifier = Modifier.weight(1f, fill = true),
        )

        Controls(
            autoPlay = autoPlay,
            isOver = isOver,
            onNext = { session.nextRound() },
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
private fun CharactersPanel(text: String) {
    PanelCard {
        Text(
            text = if (text.isBlank()) "(no characters left)" else text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
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
            enabled = !isOver && !autoPlay,
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
