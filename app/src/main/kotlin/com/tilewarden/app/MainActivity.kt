package com.tilewarden.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tilewarden.app.ui.theme.TilewardenTheme
import com.tilewarden.core.Dice
import com.tilewarden.core.Game
import com.tilewarden.core.GameEngine
import com.tilewarden.core.SilentGameObserver

/**
 * Tilewarden — Android entry point.
 *
 * Phase 2 milestone 1: prove that the :core engine runs on a real Android
 * device, the build pipeline (AGP + Compose + Kotlin 2.0) is healthy, and
 * the app shows the initial game state. No interactivity yet — we render
 * the same ASCII representation the JVM demo uses.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TilewardenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    InitialBoardScreen()
                }
            }
        }
    }
}

/**
 * One-shot board view: builds a seeded game, places everyone randomly,
 * and dumps its `toString()` into a scrollable text view.
 *
 * Replaced in Milestone 2 by a real Canvas-rendered board.
 */
@Composable
fun InitialBoardScreen() {
    val snapshot = remember {
        Dice.setSeed(2026L)
        val game = Game(
            numHeroes = 3,
            numMonsters = 4,
            boardRows = 7,
            boardColumns = 10,
            totalRounds = 20,
            observer = SilentGameObserver,
        )
        GameEngine.placeCharactersRandomly(game)
        game.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Tilewarden — initial board",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = snapshot,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1714)
@Composable
private fun InitialBoardScreenPreview() {
    TilewardenTheme {
        InitialBoardScreen()
    }
}
