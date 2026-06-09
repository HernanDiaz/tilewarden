# Tilewarden

A turn-based tactical dungeon-crawler for Android, written with Jetpack Compose.

## Status

In early development. Currently in **Phase 1: core sanitation** — extracting the
game logic into a pure-JVM module and removing all UI dependencies.

## Project layout

```
tilewarden/
├── core/                  # Pure Java game logic (no Android). Phase 1.
│   └── src/
│       ├── main/java/com/tilewarden/core/
│       └── test/java/com/tilewarden/core/
└── app/                   # Android Compose app. Added in Phase 2.
```

## Roadmap

1. **Core sanitation** — port game logic, remove UI coupling, pass JUnit tests.
2. **Android scaffold** — Compose project, run a game in a TextView.
3. **Canvas board + touch** — render and play.
4. **HUD, persistence, menu, audio.**
5. **Pre-publication polish** — icons, splash, signing, Play Console.

## Origins

Derived from a Java OOP teaching project (Universidad de Valladolid, course
*Metodología de la programación*). The original game logic is fully rewritten
and renamed; no original art, names, or rules from any commercial board game
are used.

## License

TBD.
