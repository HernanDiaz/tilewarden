package com.tilewarden.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random

/**
 * Initial / between-games screen: the player tweaks board size, party
 * size and seed, then taps "Start game" to hand the resulting
 * [GameConfig] back to [TilewardenApp].
 */
@Composable
fun SetupScreen(
    initial: GameConfig = GameConfig.Default,
    onStart: (GameConfig) -> Unit,
) {
    var seedText    by remember { mutableStateOf(initial.seed.toString()) }
    var numHeroes   by remember { mutableIntStateOf(initial.numHeroes) }
    var numMonsters by remember { mutableIntStateOf(initial.numMonsters) }
    var rows        by remember { mutableIntStateOf(initial.boardRows) }
    var columns     by remember { mutableIntStateOf(initial.boardColumns) }
    var totalRounds by remember { mutableIntStateOf(initial.totalRounds) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Tilewarden",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "New game",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = seedText,
            onValueChange = { v -> seedText = v.filter { it.isDigit() || it == '-' } },
            label = { Text("Seed") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("Same seed = same game.") },
            trailingIcon = {
                TextButton(onClick = {
                    seedText = (abs(Random.nextLong()) % 1_000_000L).toString()
                }) { Text("Random") }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        IntSliderRow(
            label = "Heroes",
            value = numHeroes,
            range = 1..5,
            onChange = { numHeroes = it },
        )
        IntSliderRow(
            label = "Monsters",
            value = numMonsters,
            range = 1..8,
            onChange = { numMonsters = it },
        )
        IntSliderRow(
            label = "Board rows",
            value = rows,
            range = 5..12,
            onChange = { rows = it },
        )
        IntSliderRow(
            label = "Board columns",
            value = columns,
            range = 5..15,
            onChange = { columns = it },
        )
        IntSliderRow(
            label = "Total rounds",
            value = totalRounds,
            range = 5..50,
            onChange = { totalRounds = it },
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val seed = seedText.toLongOrNull()
                    ?: abs(Random.nextLong()) % 1_000_000L
                onStart(
                    GameConfig(
                        seed = seed,
                        numHeroes = numHeroes,
                        numMonsters = numMonsters,
                        boardRows = rows,
                        boardColumns = columns,
                        totalRounds = totalRounds,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start game")
        }
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value.toString(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // `steps` is the count of intermediate stops, exclusive of the ends.
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}
