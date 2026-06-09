package com.tilewarden.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact dashboard replacing the per-character HUD: two side-by-side
 * panels showing live count + aggregated body bar for each side.
 *
 * Bars compare current body (sum across living teammates) against the
 * fixed total body at game start, so a dead character still counts
 * toward the denominator.
 */
@Composable
fun ScoreBoard(
    pieces: List<PieceRender>,
    initialHeroBody: Int,
    initialMonsterBody: Int,
    modifier: Modifier = Modifier,
) {
    val heroes   = pieces.filter { it.isHero }
    val monsters = pieces.filter { !it.isHero }
    val heroesBody   = heroes.sumOf { it.body }
    val monstersBody = monsters.sumOf { it.body }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TeamScore(
            label = "Heroes",
            aliveCount = heroes.size,
            currentBody = heroesBody,
            maxBody = initialHeroBody,
            modifier = Modifier.weight(1f),
        )
        TeamScore(
            label = "Monsters",
            aliveCount = monsters.size,
            currentBody = monstersBody,
            maxBody = initialMonsterBody,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TeamScore(
    label: String,
    aliveCount: Int,
    currentBody: Int,
    maxBody: Int,
    modifier: Modifier = Modifier,
) {
    val ratio = if (maxBody > 0) currentBody.toFloat() / maxBody.toFloat() else 0f
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = aliveCount.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        HealthBar(ratio = ratio, height = 8.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$currentBody / $maxBody",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
