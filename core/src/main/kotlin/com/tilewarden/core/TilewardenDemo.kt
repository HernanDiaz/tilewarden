package com.tilewarden.core

/**
 * Standalone command-line demo of the :core engine.
 *
 * Wires a [Game] to the [ConsoleGameObserver] and runs it to completion,
 * printing every event as it happens. Useful to:
 *
 * - smoke-test the engine without firing up the Android emulator
 * - record an example run to share
 * - sanity-check tweaks to the rules
 *
 * Run from Android Studio: right-click `TilewardenDemo.kt` → Run.
 * Run from the command line: `./gradlew :core:run`.
 */
fun main() {
    // Reproducible: same seed → same game.
    Dice.setSeed(2026L)

    // 3 heroes vs 4 monsters on a 7x10 board, up to 20 rounds, console-logged.
    val game = Game(
        numHeroes = 3,
        numMonsters = 4,
        boardRows = 7,
        boardColumns = 10,
        totalRounds = 20,
        observer = ConsoleGameObserver,
    )
    GameEngine.placeCharactersRandomly(game)

    println("Initial state:")
    println(game)
    println()

    GameEngine.runGame(game)

    println()
    println("Final state:")
    println(game)
}
