package com.tilewarden.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual HUD: one row per live character, with a team-coloured halo disc,
 * a name, an animated health bar, and the numeric "current/max" body.
 *
 * Heroes that have already taken their turn this round render at
 * [ACTED_ALPHA] opacity so the player sees at a glance who's still
 * available.
 */
@Composable
fun HudPanel(
    pieces: List<PieceRender>,
    actedHeroes: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pieces.isEmpty()) {
            Text(
                text = "(no characters left)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val heroes = pieces.filter { it.isHero }
            val monsters = pieces.filter { !it.isHero }
            for (p in heroes)  CharacterRow(piece = p, acted = p.name in actedHeroes)
            if (heroes.isNotEmpty() && monsters.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
            }
            for (p in monsters) CharacterRow(piece = p, acted = false)
        }
    }
}

@Composable
private fun CharacterRow(piece: PieceRender, acted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (acted) ACTED_ALPHA else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolDisc(symbol = piece.symbol, color = piece.color)

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = piece.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            HealthBar(ratio = piece.healthRatio)
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = "${piece.body}/${piece.initialBody}",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SymbolDisc(symbol: Char, color: Color) {
    val ink = Color(0xFF1B1714)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(width = 1.dp, color = ink, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol.toString(),
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun HealthBar(ratio: Float) {
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 400),
        label = "health_ratio",
    )
    val targetColor = when {
        ratio > 0.66f -> Color(0xFF7AA84A)
        ratio > 0.33f -> Color(0xFFE0B355)
        ratio > 0f    -> Color(0xFFC0524A)
        else          -> Color(0xFF6E2E2A)
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "health_color",
    )

    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animatedRatio)
                .fillMaxHeight()
                .background(animatedColor),
        )
    }
}
