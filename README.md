# Ludo-T


A Java simulation of Ludo with an extended **Ludo-T** variant that adds directional movement
(clockwise / counterclockwise via coin toss), mystery cells with teleport effects, frozen /
energised / sick piece states, blockade rules, and a home-straight qualification requirement.
The game runs headlessly and prints a full event log to stdout.

---

## Compile and Run

```powershell
# from the project root
powershell -ExecutionPolicy Bypass -File build.ps1
java -cp out Main                                   # single-process CLI simulation
```

### Client-server mode (Assignment 2)

Two processes today; the database tier makes three in a later step.

```powershell
powershell -ExecutionPolicy Bypass -File run-server.ps1
powershell -ExecutionPolicy Bypass -File run-client.ps1                          # same machine
powershell -ExecutionPolicy Bypass -File run-client.ps1 -ServerHost 192.168.1.20 # another machine
```

Run two clients and drive a game from one — the other shows the board advancing without
being asked. In the client: `create LUDO_T 42 150`, `sub g1`, `start g1`, `pause g1`,
`step g1`, `metrics`, `help`. Demos can be scripted with the `sleep <millis>` command and
piped in from a file.

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

**Domain (Assignment 1 — unchanged in its rules):**

| Package | What lives here |
|---------|-----------------|
| `engine` | Game orchestration and rule validation — start here for game flow |
| `model` | Board, Piece, Block, constants, value objects, `RandomSource` |
| `player` | Abstract and concrete players, PlayerFactory |
| `player.strategy` | Four piece-selection strategies (one per colour) |
| `logger` | Event publisher and Logger (Observer pattern) |
| `enums` | Colour, Direction, GameMode, PieceEffect, PieceState, TeleportDest |

**Client-server tiers (Assignment 2), listed inner layer first:**

| Package | Layer | What lives here |
|---------|-------|-----------------|
| `app` | Use cases | `GameSession` (actor), `SessionRegistry`, `SnapshotFactory` |
| `app.usecase` | Use cases | One interactor per use case: create, start, pause, resume, step, abort, set speed, list, snapshot, subscribe |
| `app.port` | Use cases | Output ports: `EventSink`, `GameRepository`, `Clock`, `SessionListenerFactory` |
| `app.model` | Use cases | Immutable output models the interactors return |
| `adapter` | Interface adapters | `GameEventBroadcaster`, `RequestRouter`, controllers, DTO mappers |
| `net` | Frameworks | Sockets, `RequestQueue`, `ClientConnection`, `ConnectionRegistry` |
| `shared` | Frameworks | Wire DTOs and `ProtocolCodec` — the only package on both classpaths |
| `server` | Frameworks | `ServerAssembly` (composition root), `ServerMain` |
| `client` | Frameworks | `SmokeClient` today; the Swing GUI replaces it |

The dependency rule is enforced by import direction: `model`, `enums`, `engine`, `player` and
`logger` never import `app`, `adapter`, `net`, `shared`, `server` or `client`.

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
| 1 | **Singleton** | `NoPiece` — immutable, so one shared instance is safe. **`Dice` and `RandomInitiator` were also singletons and are no longer**: a process-wide mutable `Random` was correct single-threaded but became a correctness bug once the server hosted concurrent games (creating a seeded game reseeded games already running, and concurrent games consumed each other's stream). Replaced by an injected per-game `RandomSource`; `getInstance()` survives only as the default for the CLI path. See `RandomSourceIsolationTest` |
| 2 | **Builder** | `GameEngineBuilder` — fluent API for mode and seed before constructing `GameEngine` |
| 3 | **Factory Method** | `PlayerFactory.createPlayers()` — creates all four players and wires their strategies |
| 4 | **Strategy** | `PieceSelectionStrategy` interface + `AggressiveStrategy`, `BlockerStrategy`, `RacerStrategy`, `MysteryHunterStrategy` |
| 5 | **Template Method** | `AbstractPlayer` — defines the shared player structure (piece ownership, board queries); concrete subclasses supply only colour and name |
| 6 | **State** | `PieceState` enum (`BASE`, `ACTIVE`, `HOME`) drives `Piece` behaviour throughout the engine — base-exit rules, board queries, and win detection all branch on state |
| 7 | **Façade** | `GameEngine` — single entry point hiding `TurnExecutor`, `EffectHandler`, `WinTracker`, `FirstPlayerSelector` |
| 8 | **Null Object** | `NoPiece` — returned by strategies when no valid piece exists; eliminates null checks in the engine |
| 9 | **DTO** | `MoveResult`, `BlockMoveResult` — carry validation results from `RuleEngine` to `TurnExecutor` with no behaviour of their own |
| 10 | **Observer** | `GameEventPublisher` (subject) → `GameEventListener` (interface) → `Logger` and `adapter.GameEventBroadcaster` (concrete observers). Because this port already existed, making the game network-visible needed **no change to any engine class** |
| 11 | **Actor** | `app.GameSession` — one thread owns one game, so the engine needs no locks and stays the single-threaded code Assignment 1 tested, while different games still run in parallel |
| 12 | **Adapter / Gateway** | `adapter.SnapshotMapper`, `adapter.ServerEventMapper` — the only classes that know both `app.model` and `shared` |
| 13 | **Proxy** | `net.ClientConnection` — a controller calls `ClientSession` methods without knowing a socket is on the other side |
| 14 | **Composition root** | `server.ServerAssembly` — the one place dependencies are constructed, which is what makes every layer beneath substitutable in a test |

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
