package com.tilewarden.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small disc echoing the on-board piece: filled circle with a 1px ink
 * border and the character's symbol letter centred in [symbolInk] black.
 */
@Composable
internal fun SymbolDisc(
    symbol: Char,
    color: Color,
    diameter: Dp = 28.dp,
) {
    val ink = Color(0xFF1B1714)
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(color)
            .border(width = 1.dp, color = ink, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol.toString(),
            color = ink,
            fontWeight = FontWeight.Bold,
            fontSize = (diameter.value * 0.5f).sp,
        )
    }
}
