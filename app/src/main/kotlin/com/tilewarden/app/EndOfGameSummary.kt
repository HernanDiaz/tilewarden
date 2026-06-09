package com.tilewarden.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tilewarden.core.Side

private val HEROES_VERDICT_COLOR    = Color(0xFFF0C969)  // gold
private val MONSTERS_VERDICT_COLOR  = Color(0xFFE25D4A)  // terracotta
private val DRAW_VERDICT_COLOR      = Color(0xFFB89770)  // muted parchment

/**
 * Modal overlay that wraps up the match:
 * - Verdict banner, coloured per winning side.
 * - Round summary line.
 * - Two-column stats (attacks + damage per side).
 * - Survivors list with health bars.
 * - MVP card highlighting whoever dealt the most damage.
 * - Three buttons: Rematch, New game, Close.
 *
 * Pops in with a scale + fade animation. If [showConfetti] is true,
 * a [Confetti] layer rains down behind the card.
 */
@Composable
fun EndOfGameSummary(
    session: GameSession,
    showConfetti: Boolean,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onClose: () -> Unit,
) {
    val winner = session.winner
    val verdictColor = when (winner) {
        Side.HEROES   -> HEROES_VERDICT_COLOR
        Side.MONSTERS -> MONSTERS_VERDICT_COLOR
        Side.DRAW     -> DRAW_VERDICT_COLOR
        null          -> DRAW_VERDICT_COLOR
    }
    val verdictText = when (winner) {
        Side.HEROES   -> "Heroes win"
        Side.MONSTERS -> "Monsters win"
        Side.DRAW     -> "Draw"
        null          -> "Game over"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Confetti behind the card.
        if (showConfetti) {
            Confetti(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = true,
            enter = scaleIn(initialScale = 0.6f, animationSpec = tween(280)) +
                fadeIn(animationSpec = tween(280)),
            exit = scaleOut(targetScale = 0.6f, animationSpec = tween(180)) +
                fadeOut(animationSpec = tween(180)),
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* swallow taps inside the card */ },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VerdictHeader(
                        text = verdictText,
                        color = verdictColor,
                        round = session.round.coerceAtMost(session.totalRounds),
                        totalRounds = session.totalRounds,
                    )

                    StatsBlock(session)

                    SurvivorsBlock(session)

                    if (winner != null) {
                        MvpBlock(session, winner)
                    }

                    Spacer(Modifier.height(4.dp))

                    ActionButtons(
                        onRematch = onRematch,
                        onNewGame = onNewGame,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictHeader(text: String, color: Color, round: Int, totalRounds: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Text(
            text = "Round $round of $totalRounds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsBlock(session: GameSession) {
    val s = session.statistics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SideStats(
            label = "HEROES",
            color = HEROES_VERDICT_COLOR,
            attacks = s.heroAttacks,
            damage = s.heroDamageDealt,
            modifier = Modifier.weight(1f),
        )
        SideStats(
            label = "MONSTERS",
            color = MONSTERS_VERDICT_COLOR,
            attacks = s.monsterAttacks,
            damage = s.monsterDamageDealt,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SideStats(
    label: String,
    color: Color,
    attacks: Int,
    damage: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        StatLine("Attacks", attacks)
        StatLine("Damage", damage)
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SurvivorsBlock(session: GameSession) {
    val survivors = session.pieces
    if (survivors.isEmpty()) {
        Text(
            text = "No survivors",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Survivors",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (piece in survivors) {
            SurvivorRow(piece)
        }
    }
}

@Composable
private fun SurvivorRow(piece: PieceRender) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SymbolDisc(symbol = piece.symbol, color = piece.color, diameter = 22.dp)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = piece.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HealthBar(ratio = piece.healthRatio, height = 4.dp)
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = "${piece.body}/${piece.initialBody}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MvpBlock(session: GameSession, winner: Side) {
    val mvp = session.mvpFor(winner) ?: return
    val damage = session.damageByName[mvp.name] ?: 0
    val attacks = session.attacksByName[mvp.name] ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "MVP",
            style = MaterialTheme.typography.labelMedium,
            color = HEROES_VERDICT_COLOR,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolDisc(symbol = mvp.symbol, color = mvp.color, diameter = 32.dp)
            Spacer(Modifier.padding(horizontal = 10.dp))
            Column {
                Text(
                    text = mvp.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$damage damage in $attacks attack${if (attacks == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRematch,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rematch") }
        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("New game") }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Close") }
    }
}
