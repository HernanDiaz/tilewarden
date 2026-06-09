package com.tilewarden.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tilewarden.app.ui.theme.TilewardenTheme

/**
 * Tilewarden — Android entry point.
 *
 * Phase 2 milestone 2: the game is now interactive — a [GameSession] state
 * holder drives a [com.tilewarden.core.Game] one round at a time, and the
 * UI offers Next-round / Auto-play / Reset controls and a scrolling event
 * log. Still text-only; milestone 3 will swap the ASCII board for Canvas.
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
                    val session = rememberGameSession()
                    GameScreen(session)
                }
            }
        }
    }
}
