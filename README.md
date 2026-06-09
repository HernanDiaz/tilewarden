# Tilewarden

A turn-based tactical dungeon-crawler — Android first, desktop (Steam) planned.

Written in Kotlin, planned UI on Jetpack Compose (Android) and Compose
Multiplatform (desktop).

## Status

**Phase 1 — Core sanitation: complete.** The full game model has been ported
from the original Java codebase to idiomatic Kotlin under `:core`, all Swing /
console coupling has been removed, and an event-based observer abstraction
isolates the engine from any UI. 80+ tests cover stats, combat, defence rules,
board mechanics, AI behaviour and a full end-to-end game loop.

Phase 2 (Android `:app` module with Compose) is the next milestone.

## Project layout

```
tilewarden/
├── core/                       # Pure-JVM game logic. No Android, no Swing.
│   ├── build.gradle.kts        # Kotlin 2.0 + JUnit 5 + application plugin
│   └── src/
│       ├── main/kotlin/com/tilewarden/core/
│       │   ├── Direction.kt        — cardinal enum
│       │   ├── XYLocation.kt       — immutable 2D position
│       │   ├── Dice.kt             — seedable RNG singleton
│       │   ├── Weapon.kt           — name + attack dice
│       │   ├── Piece.kt            — anything occupying a square
│       │   ├── Square.kt           — a cell
│       │   ├── Board.kt            — the grid + move/remove API
│       │   ├── Statistics.kt       — per-game tally
│       │   ├── Character.kt        — abstract base (stats, AI hooks)
│       │   ├── Hero.kt             — heroes (5-6 block, wield Weapon)
│       │   ├── Monster.kt          — monsters (6 only block)
│       │   ├── Barbarian.kt        — Hero subclass (Broadsword 3d)
│       │   ├── Dwarf.kt            — Hero subclass (Hand axe 2d)
│       │   ├── Goblin.kt           — Monster subclass (fast, fragile)
│       │   ├── Mummy.kt            — Monster subclass (slow, tough)
│       │   ├── Game.kt             — match state + observer
│       │   ├── GameEngine.kt       — rule helpers + game loop
│       │   ├── GameEvent.kt        — sealed event hierarchy + observers
│       │   └── TilewardenDemo.kt   — runnable demo (main)
│       └── test/kotlin/com/tilewarden/core/
│           ├── XYLocationTest, DiceTest, BoardTest, …
│           ├── CharacterAndSubclassesTest, DefenseRulesTest
│           ├── GameTest, GameEngineTest
│           ├── CombatIntegrationTest
│           ├── GameLoopSmokeTest
│           └── OriginalCourseExercisesTest
└── app/                        # Android Compose app — Phase 2.
```

## How to run / test

The Gradle wrapper bootstraps everything; you only need a JDK on the path
(any 17 or 21 will do — Android Studio ships one as `D:\AndroidStudio\jbr`).

```powershell
# Compile + run the full test suite (80+ tests)
./gradlew :core:test

# Run the demo: a complete 20-round game printed to stdout
./gradlew :core:run
```

From Android Studio: open `tilewarden/` as a Gradle project. Right-click
`TilewardenDemo.kt` → **Run** for the demo; right-click any `*Test.kt` →
**Run** for a single test class.

## Architecture notes

### Event-based UI decoupling

The original Java sprinkled `System.out.println` and Swing repaints throughout
the model (`Personaje.accionMovimiento` called `Jeroquest.monitor.muestraPartida()`
directly). Phase 1 cuts that wire by introducing a sealed `GameEvent` and a
`GameObserver` interface. Three observers ship in the box:

- `ConsoleGameObserver` — pretty-prints each event to stdout (default for demo).
- `SilentGameObserver` — discards everything (default for tests and headless runs).
- `RecordingGameObserver` — collects every event in order; used by tests to
  assert on outcomes.

The Android UI (Phase 2) will plug in its own `GameObserver` that translates
events into Compose state updates and animations. The engine doesn't know
or care.

### Dropped from the original

- `VentanaJeroquest`, `MiJLabelPersonaje`, `MiPanelTablero`, `MiTeclado`,
  `Jeroquest_Test` — Swing / console UI, replaced by the observer abstraction.
- `ElementoGrafico` — the `javax.swing.Icon` interface. Replaced by a plain
  `String spriteId` per concrete character; the UI layer maps it to its
  actual asset.
- `VectorDinamicoPersonajes`, `VectorDinamicoObjects`, `VectorDinamicoXYLocation`
  — hand-rolled dynamic arrays for a teaching exercise; replaced by Kotlin's
  standard `MutableList<T>`.

## Roadmap

1. **Core sanitation** — done.
2. **Android scaffold** — `:app` module, Compose entry point, run a game with
   `ConsoleGameObserver` to a `TextView` first.
3. **Canvas board + touch** — render the grid, sprites, touch-to-move,
   touch-to-attack.
4. **HUD, persistence, menu, audio.**
5. **Pre-publication polish** — icons, splash, signing, Play Console.
6. **Desktop port** via Compose Multiplatform → Steam.

## Origins

Derived from a Java OOP teaching project (Universidad de Valladolid, course
*Metodología de la programación*). The original game logic is fully rewritten
and renamed; no original art, names, or rules from any commercial board game
are used.

## License

TBD.
