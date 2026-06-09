package com.tilewarden.app

import androidx.compose.runtime.Immutable

/**
 * User-tunable parameters that define a new match.
 *
 * Owned by [TilewardenApp] and passed to [GameSession] at construction.
 * Keying [GameSession] on this data class also gives us a clean way to
 * recycle the session when the user starts a new game with different
 * settings.
 */
@Immutable
data class GameConfig(
    val seed: Long,
    val numHeroes: Int,
    val numMonsters: Int,
    val boardRows: Int,
    val boardColumns: Int,
    val totalRounds: Int,
) {
    companion object {
        val Default: GameConfig = GameConfig(
            seed = 2026L,
            numHeroes = 3,
            numMonsters = 4,
            boardRows = 7,
            boardColumns = 10,
            totalRounds = 20,
        )
    }
}
