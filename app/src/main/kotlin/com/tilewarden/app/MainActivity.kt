package com.tilewarden.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tilewarden.app.ui.theme.TilewardenTheme

/**
 * Tilewarden — Android entry point.
 *
 * Two-screen navigation:
 * - [SetupScreen] until the player commits a [GameConfig].
 * - [GameScreen] (driven by a [GameSession]) once a config exists.
 *
 * Tapping "Menu" inside the game clears the config and returns to setup.
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
                    TilewardenApp()
                }
            }
        }
    }
}

@Composable
private fun TilewardenApp() {
    // Track both the active config AND the last one the user committed to,
    // so the setup screen can re-open with the same values pre-filled.
    var lastConfig by remember { mutableStateOf(GameConfig.Default) }
    var activeConfig: GameConfig? by remember { mutableStateOf(null) }

    // One AudioEngine for the whole process. Synthesises every SFX once at
    // construction (materialised as WAVs in cacheDir for SoundPool) and
    // keeps the mute flag across setup ↔ game transitions.
    val appContext = LocalContext.current.applicationContext
    val audio = remember { AudioEngine(appContext) }

    val cfg = activeConfig
    if (cfg == null) {
        SetupScreen(
            initial = lastConfig,
            onStart = { committed ->
                lastConfig = committed
                activeConfig = committed
            },
        )
    } else {
        // remember(cfg) means a brand-new GameSession every time the user
        // commits a fresh config — clean reset of state, log, RNG, etc.
        val session = remember(cfg) {
            GameSession(
                seed = cfg.seed,
                numHeroes = cfg.numHeroes,
                numMonsters = cfg.numMonsters,
                boardRows = cfg.boardRows,
                boardColumns = cfg.boardColumns,
                totalRounds = cfg.totalRounds,
                audio = audio,
            )
        }
        GameScreen(
            session = session,
            audio = audio,
            onBackToMenu = { activeConfig = null },
        )
    }
}
