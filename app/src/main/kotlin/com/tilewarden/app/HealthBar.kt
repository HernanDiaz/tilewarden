package com.tilewarden.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontal coloured bar that fills proportionally to [ratio] (0..1).
 *
 * The width animates over [tween]/400 ms; the colour ramps from green to
 * amber to red as the ratio drops. Used by both [ScoreBoard] and
 * [PieceInspector].
 */
@Composable
internal fun HealthBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
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
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
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
