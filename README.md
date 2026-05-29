# Ludo-T


A Java simulation of Ludo with an extended **Ludo-T** variant that adds directional movement
(clockwise / counterclockwise via coin toss), mystery cells with teleport effects, frozen /
energised / sick piece states, blockade rules, and a home-straight qualification requirement.
The game runs headlessly and prints a full event log to stdout.

---

## Compile and Run

```bash
# from the project root
javac -d out -sourcepath src $(find src -name "*.java" | tr '\n' ' ')
java -cp out Main
```

To change the game mode, edit `Main.java`:

```java
GameEngine game = new GameEngineBuilder()
        .withMode(GameMode.LUDO_T)   // or GameMode.CLASSIC
        .withSeed(42L)               // optional: fixed seed for reproducibility
        .build();
game.startGame();
```

---

## Navigating the Code

### Entry point

`Main.java` → `GameEngineBuilder.build()` → **`GameEngine.startGame()`**

Everything flows from `startGame()`. Follow that call chain to explore any feature.

### Package layout

| Package | What lives here |
|---------|-----------------|
| `engine` | Game orchestration and rule validation — start here for game flow |
| `model` | Board, Piece, Block, constants, value objects |
| `player` | Abstract and concrete players, PlayerFactory |
| `player.strategy` | Four piece-selection strategies (one per colour) |
| `logger` | Event publisher and Logger (Observer pattern) |
| `enums` | Colour, Direction, GameMode, PieceEffect, PieceState, TeleportDest |

Each package has a `package-info.java` that describes the classes and their responsibilities.

### Key classes at a glance

```
GameEngine            Facade — lifecycle, round loop, mystery cell
  └─ TurnExecutor     Executes one turn: dice, frozen checks, move application
       └─ EffectHandler   Frozen tick/escape, coin toss, teleport effects
  └─ WinTracker       Finishing order, game-over flag
  └─ FirstPlayerSelector  Tie-break roll to decide who goes first
  └─ RuleEngine       Validates moves, resolves blocks, checks captures, home entry
  └─ TurnManager      Dice wrapper, consecutive-roll tracking, turn order

Board                 Canonical piece positions; helpers: projectPosition, getBlockAt, getAdjacentCell
Piece                 Position, direction, effect — mutated via moveTo / incrementApproachPass / etc.
```

---

## Design Patterns

| # | Pattern | Where |
|---|---------|-------|
| 1 | **Singleton** | `Dice`, `RandomInitiator`, `NoPiece` — each class guarantees one instance |
| 2 | **Builder** | `GameEngineBuilder` — fluent API for mode and seed before constructing `GameEngine` |
| 3 | **Factory Method** | `PlayerFactory.createPlayers()` — creates all four players and wires their strategies |
| 4 | **Strategy** | `PieceSelectionStrategy` interface + `AggressiveStrategy`, `BlockerStrategy`, `RacerStrategy`, `MysteryHunterStrategy` |
| 5 | **Template Method** | `AbstractPlayer` — defines the shared player structure (piece ownership, board queries); concrete subclasses supply only colour and name |
| 6 | **State** | `PieceState` enum (`BASE`, `ACTIVE`, `HOME`) drives `Piece` behaviour throughout the engine — base-exit rules, board queries, and win detection all branch on state |
| 7 | **Façade** | `GameEngine` — single entry point hiding `TurnExecutor`, `EffectHandler`, `WinTracker`, `FirstPlayerSelector` |
| 8 | **Null Object** | `NoPiece` — returned by strategies when no valid piece exists; eliminates null checks in the engine |
| 9 | **DTO** | `MoveResult`, `BlockMoveResult` — carry validation results from `RuleEngine` to `TurnExecutor` with no behaviour of their own |
| 10 | **Observer** | `GameEventPublisher` (subject) → `GameEventListener` (interface) → `Logger` (concrete observer) |

---

## Asumptions and Simplifications

**How does a turn work?**  
`GameEngine.executeRound()` iterates players → `TurnExecutor.executeTurn()` → validates with
`RuleEngine` → applies changes to the `Board` and `Piece` objects → publishes events.

**How does block movement work?**  
`TurnExecutor.buildBlockForPiece()` checks whether the chosen piece is part of a block via
`Board.getBlockAt()`. If so, `RuleEngine.validateBlockMove()` validates it and
`RuleEngine.resolveBlock()` moves all pieces in the block together.

**How does the home straight work?**  
CW pieces: `RuleEngine.isHomeStraightEntry()` detects when a roll crosses the approach cell.  
CCW pieces: `Piece.approachPassCount` is incremented by `TurnExecutor.trackApproachPass()`;
after two passes, `RuleEngine.isHomeMoveForCCW()` allows home entry.

**How do mystery-cell teleports work?**  
`RuleEngine.applyLudoTRules()` tags a `MoveResult` with a `TeleportDest`.
`EffectHandler.handleTeleport()` reads that destination and moves the piece + applies the
matching effect (frozen, energised, sick, direction flip).

**Where are all the tunable constants?**  
`model.GameConstants` — board size, effect durations, cell IDs (ALPHA/BETA/GAMMA),
consecutive-roll thresholds, finishing count, etc.
