# Ludo-T Assignment 2 — Implementation Notes

A reference for how the client-server system was built, and **why** each decision was made.
Written to be the source material for the report, so most sections state a trade-off rather
than just a fact.

> **Companion document: [`DESIGN-RATIONALE.md`](DESIGN-RATIONALE.md).** This file records
> *what* was built. That one records **how each decision was reached, what evidence supports
> it, and what it costs** — with diagrams, an evidence ledger (🟢 measured / 🟡 derived /
> 🔵 reasoned / 🔴 pending), the benchmark plan, and the list of options deliberately cut.
> It is written as a standalone handoff for another agent. Read it first when resuming.

**Module:** COMP63038 · **Weight:** 50% · **Due:** 09 Oct 2026
**Status:** plan steps 1–4 complete. Persistence (step 5), load clients (step 6) and the
report (step 7) remain.

### How this document is curated

Every significant design decision gets recorded here in the same shape, **including the paths
not taken**. The rubric's top band for Critical discussion (20%) reads "*all* appropriate
concurrency mechanisms discussed with insightful justification" — so an alternative that was
considered and rejected is worth as much as the one that shipped, and only this document
preserves it. A rejected option that goes unwritten is indistinguishable from one that was
never thought of.

The shape, applied to every decision from here on:

1. **The forcing constraint** — what about the problem made this a real choice
2. **Options considered** — each with its actual failure mode, not a caricature
3. **What was chosen, and the cost paid** — every choice buys something and spends something
4. **Evidence** — labelled honestly as *measured* or *reasoned*; never blur the two
5. **Known weaknesses of the chosen option** — volunteered, not discovered by the assessor

Point 5 is the one that earns "insightful". Demo instruction: *"if asked, you must be able to
explain in detail the software you present"* — a weakness you raise yourself reads as mastery;
the same weakness found by a tutor reads as a gap.

---

## 1. What changed, at a glance

Assignment 1 was a single process: `Main` → `GameEngineBuilder.build()` → a blocking
`while (!gameOver) executeRound()` loop, with `Logger` printing to stdout.

Assignment 2 is the same game rules running inside a three-process system:

```
┌─────────────┐   Swing GUI      ┌─────────────┐   sockets    ┌──────────────┐
│  client/ui  │ ───────────────▶ │   server    │ ───────────▶ │  H2 database │
│  (process)  │ ◀─────────────── │  (process)  │              │  (process)   │
└─────────────┘   server pushes  └─────────────┘              └──────────────┘
                                                                 (step 5, not yet built)
```

**The game rules did not change.** All 138 original tests still pass untouched. That is the
central claim of the Architecture section, and it is verifiable by running the suite.

---

## 2. Layers and the dependency rule

```
Entities              model/  enums/                      ← unchanged, plus RandomSource
Use cases             engine/  player/  player.strategy/  ← unchanged rules
                      app/  app.usecase/  app.port/  app.model/     ← NEW
Interface adapters    adapter/                                      ← NEW
Frameworks & drivers  net/  ui/  shared/  server/  client/          ← NEW
```

**The rule, enforced by import direction:** `model`, `enums`, `engine`, `player` and `logger`
never import `app`, `adapter`, `net`, `shared`, `server` or `client`.

To check it holds:

```powershell
# Should return nothing.
Select-String -Path src\model\*.java,src\engine\*.java,src\player\*.java `
              -Pattern "import (app|adapter|net|shared|server|client|ui)\."
```

### Why `app.model` duplicates `shared`

`app.model.GameSnapshot` and `shared.BoardSnapshot` carry nearly identical fields. Merging
them would delete `SnapshotMapper` — and would make the use-case layer depend on the wire
format, so renaming a protocol field would force the interactors to be recompiled and
retested. Keeping them separate is the standard use-case boundary: interactors return output
models, adapters convert those to whatever the delivery mechanism needs.

`adapter/SnapshotMapper` is the only class in the codebase importing both vocabularies.

---

## 3. Step 1 — the RNG singleton bug (the report's worked example)

### The bug

`model/RandomInitiator` held a **process-wide mutable `Random`**; `model/Dice` was a singleton
over it; `GameEngineBuilder.build()` called `RandomInitiator.getInstance().setSeed(seed)` — a
global side effect.

Correct single-threaded. Under concurrency, two failures:

1. Creating a seeded game **reseeded every game already running**.
2. Concurrent games **consumed each other's number stream**, so seeded runs stopped being
   reproducible.

`Random.nextInt` is thread-safe. Thread safety was never the problem — **shared mutable
state** was. That distinction is the point worth making in the report.

### The fix

A `model/RandomSource` port (`int nextInt(int bound)`), threaded per-game through:

```
GameEngineBuilder → GameEngine → TurnManager      → Dice
                               → EffectHandler    (coin toss, alpha effect)
                               → MysteryCellManager → MysteryCell (destination, placement)
```

`FirstPlayerSelector` takes its dice from `TurnManager.getDice()` so selection rolls share the
game's stream. Every old constructor was kept, delegating to a shared default, so all 18
original test classes compiled unchanged.

`build()` now always creates a **fresh** `RandomInitiator` per engine. Seeding never touches
the singleton.

### The measured evidence

`test/RandomSourceIsolationTest.java`. Verified by stashing the fix and re-running:

| Scenario | Before fix | After fix |
|---|---|---|
| Seeded game run alone | 970 rounds | 970 rounds |
| Same game, another seeded game built mid-run | **928** ❌ | 970 ✓ |
| 8 same-seed games run concurrently | **633** ❌ | 970 ✓ |

Concrete before/after numbers beat an abstract argument about singletons.

---

## 4. Step 2 — `shared/`, `app/`, `adapter/`

### `shared/` — the wire contract

The only package on **both** classpaths.

| Type | Purpose |
|---|---|
| `Request` / `Response` / `Command` | request-reply, correlated by id |
| `ServerEvent` / `ServerEventType` | the push half |
| `BoardSnapshot`, `PlayerDto`, `PieceDto`, `SessionSummaryDto`, `ServerMetricsDto` | DTOs (immutable records) |
| `ProtocolCodec` | framing over a socket |

**Why these types hold no logic:** they cross a process boundary, so anything they *did* would
have to exist and behave identically in two applications. Inert DTOs also mean no client ever
holds a reference to a domain `Piece` or `Board`, so no client can mutate server state.

#### Two load-bearing details in `ProtocolCodec`

1. **Handshake order.** Creating an `ObjectOutputStream` writes a header; creating an
   `ObjectInputStream` *blocks* reading the peer's. If both ends built input first they would
   deadlock before exchanging a byte. `open(Socket)` always builds output → flush → input, and
   **both peers use that one method**, so the ordering cannot drift apart.

2. **`reset()` after every frame.** `ObjectOutputStream` caches objects it has written and
   sends a back-reference next time. Without `reset()` a client receives the *first* board
   forever while the game visibly advances on the server. Guarded by
   `should_deliverLatestState_when_sameObjectIsSentTwiceAfterMutation`.

### `app/` — use cases

- `GameSession` — one hosted game, run as an **actor**
- `SessionRegistry` — every live game, `ConcurrentHashMap`-backed
- `SnapshotFactory` — photographs a live engine into an immutable snapshot
- `app.usecase.*` — one interactor per use case (create, start, pause, resume, step, abort,
  set speed, list, snapshot, subscribe, unsubscribe)
- `app.port.*` — `EventSink`, `GameRepository`, `Clock`, `SessionListenerFactory`

`java.util.concurrent` is used here. That is a deliberate line: an executor is a
standard-library tool for structuring work, not a framework, and no code here knows whether
work arrived from a socket, a GUI button or a test.

### `adapter/` — and the architectural payoff

**`GameEventBroadcaster` is the centrepiece.** `logger.GameEventListener` was *already* an
output port in Assignment 1, with `Logger` as its only implementation. The broadcaster is a
second implementation, registered exactly where `Logger` sat. The engine calls
`publisher.publishCapture(...)` precisely as before and has no idea the event now leaves over
a socket.

> **Not one line of `GameEngine`, `GameEventPublisher` or any other engine class changed to
> make the game network-visible.**

That is what "the domain does not depend on the delivery mechanism" means in practice rather
than in a diagram.

The broadcaster's wording is shorter than `Logger`'s. `Logger` still emits the assignment's
spec-exact verbose console lines on the CLI path; a scrolling GUI panel has different needs.
Same events, two presentations — which is the point of having a port.

---

## 5. Step 3 — `net/` and `server/`

### Threading model

| Thread | Responsibility | Must never |
|---|---|---|
| accept (`LudoServer`) | handshakes, register connections | do per-request work |
| client-reader (`ClientConnection`) | decode frames, **enqueue** | execute a request |
| request-worker (`RequestQueue`) | route and execute | touch an engine directly |
| game actor (`GameSession`) — **virtual** | **all** engine mutation | write to a socket |
| client-writer (`ClientConnection`) | drain outbound queue | block anyone else |

#### Mechanism inventory — the answer to "but did you use multithreading?"

Confinement is a concurrency *mechanism*, not the avoidance of one, and it is far from the only
one here. Learning outcome 4 asks to "justify the selection made", which presumes some were
selected and others rejected. For the record, what is actually running:

| Mechanism | Where |
|---|---|
| dedicated thread | accept loop; per-connection writer; client receiver |
| cached thread pool | client readers (`LudoServer`) |
| bounded `ThreadPoolExecutor` + `ArrayBlockingQueue` + CallerRuns backpressure | `RequestQueue` |
| thread confinement (actor) + virtual threads | `GameSession` |
| `ScheduledExecutorService` | session ticks; metrics broadcast |
| bounded queue with oldest-drop | per-connection outbound |
| `ConcurrentHashMap` | `SessionRegistry`; client's pending-request map |
| `volatile`, `AtomicInteger`, `AtomicLong` | session state, tick interval, subscriber counts, id sequence |
| `CompletableFuture` | every use case's return path |
| event-dispatch-thread confinement | Swing client (§6) |
| **rejected:** `synchronized`, `ReentrantLock`, fine-grained locks, immutable/copy-on-write, per-game lock + shared pool | discussed below and in §5's mechanism table |

The framing if challenged: the concurrency lives in the **server tier, where it belongs**,
rather than being smeared through the domain — which is simultaneously the Clean Architecture
argument, so it earns under two criteria.

### Why an actor per game rather than locks

A `GameEngine` is a deep graph of mutable objects — board, pieces, players, turn manager.
Guarding it with `synchronized` means either one coarse lock (correct, but serialises
unrelated games) or many fine locks (fast, and a standing invitation to deadlock as an
operation walks board → piece → player).

Confining each game to one thread removes the question: there is no shared access to guard, so
**the engine needs no locks and no `synchronized` anywhere**, and it stays exactly the
single-threaded code Assignment 1 tested.

This does not serialise the server — each session owns a separate thread, so twenty games
genuinely advance in parallel. Only operations on the *same* game queue behind one another,
which is the ordering a game wants anyway: a pause arriving mid-round takes effect at the
round boundary instead of tearing the board in half.

#### The three lock designs, and what each would actually cost

| Design | Verdict |
|---|---|
| **One global lock** | Correct, and useless — every game waits for every other. A multi-user server that plays one game at a time. |
| **One lock per game** | The **legitimate rival**, not a strawman. Same mutual exclusion, same correctness. Loses on two points below. |
| **Fine-grained locks** (per piece / per cell) | Deadlock risk, and it retracts the project's central claim. |

*Why one lock per game still loses.* First, **the caller waits**: a request-worker thread
would hold the lock for the whole round instead of posting a job and returning, so a slow round
propagates backwards into `RequestQueue` saturation and CallerRuns throttling. Second,
**ordering**: `ReentrantLock` is unfair by default, so a `pause` has no guaranteed position and
can be repeatedly jumped by tick acquisitions; a fair lock fixes that and costs throughput. A
mailbox is FIFO by construction.

*Why fine-grained locks are worse than they look here.* `GameEventBroadcaster` is registered as
a `GameEventListener`, so **the engine calls out to adapter code in the middle of
`executeRound()`**, and that code reaches `EventSink` → connection queues. Holding a lock while
invoking code the engine does not control is the canonical deadlock recipe: lock ordering would
depend on adapter behaviour the domain cannot see. Add that a capture already touches three
objects (a cell, the moving piece, an opponent's piece) in an order every future change must
respect, and the invariant is enforced by nothing but memory.

The decisive objection, though, is architectural. Fine-grained locks live *inside* the engine,
so every engine method needs annotation — which retracts §1's central claim that the game rules
did not change and the original tests still pass untouched. Confinement is what makes that
claim true.

### How the actor actually works

An actor is three things: a **mailbox**, **one worker**, and **the state it owns**.

| Part | In this code |
|---|---|
| mailbox | the queue inside `Executors.newSingleThreadExecutor` |
| worker | the virtual thread named `game-g1` |
| owned state | that session's `GameEngine` |

Nothing calls the engine directly. Callers post a job and receive a receipt —
`submit()`/`supply()` (`GameSession.java:294`) hand back a `CompletableFuture` and return
immediately. The single-threaded executor then runs jobs **one at a time, in the order
posted**, which is the whole trick: one worker plus a queue gives mutual exclusion without a
lock, *and* ordering, which a lock does not give.

A Pause click touches five threads and exactly one of them goes near the engine:

```
1. client sender      writes the frame
2. client-reader      decodes, enqueues into RequestQueue
3. request-worker     routes → PauseGameUseCase → session.pause()
                      posts the job, takes the receipt, moves on   ← the key moment
4. game-g1 (actor)    runs the job, touches the engine             ← the only engine access
5. client-writer      pushes the resulting event back out
```

Ticking obeys the same rule: the scheduler thread does **not** run the round, it posts a job
(`GameSession.java:229`) and leaves. Between ticks the mailbox is empty and the actor is
parked — which is exactly why a virtual thread fits, and why a `pause` arriving mid-round is
simply the next letter rather than an interruption.

### What multiple users actually experience

**Different games — fully parallel.** Separate mailboxes, separate threads, separate engines.
No user waits on another.

**The same game — one queue, arrival order.** This is a feature, not a limit: several users act
on one shared game, so there must be one order. Alice's pause and Bob's step cannot both
half-happen; the queue decides who was first and both receive a definite answer. The result is
then pushed to *every* subscriber (`EventSink` → each connection's outbound queue), so Bob's
step appears on Alice's screen unasked — which is precisely the client-side rubric's top band,
"when one client changes data that is displayed on another client, the other client will
immediately show the change".

**A hundred requests at once.** They land in `RequestQueue` first. Nothing is dropped; when the
queue is full, CallerRuns makes the flooding connection do the work itself, throttling that
client rather than everyone. That is the server-side rubric's top band.

### What confinement costs — the honest list

Volunteer these; do not let them be found.

1. **Reads queue too.** `snapshot()` waits behind a running round, where a `ReadWriteLock`
   would let readers run concurrently. Cheap here — rounds are ~1 ms.
2. **Confinement is already broken in one place, deliberately.** `summary()`
   (`GameSession.java:202`) reads `engine.getRoundNumber()` from *any* thread, off the actor.
   That is a real data race, benign because a stale round number in a lobby listing harms
   nobody, and taken to stop every listing from queueing behind every game. It is the one crack
   in "nothing is shared" and should be stated first, not defended when caught.
3. **Nothing can be atomic across two games.** Confinement gives exclusivity per game and no
   way to hold two at once. Irrelevant to Ludo; it is the design's actual structural limit.
4. **Deadlock is not gone, only disguised.** If an actor blocks on another actor's future while
   that actor waits on its own, the server hangs with no lock to point at. Concretely: calling
   `.join()` on another session's future from inside an actor task. This code never does — but
   "actors cannot deadlock" is false.
5. **Everything becomes asynchronous.** `CompletableFuture` through every use case, stack
   traces split across threads, harder debugging.

The summary worth saying out loud: confinement does not remove concurrency problems, it
**concentrates** them — here into a handful of `volatile` fields and the event sink, instead of
smearing them across every method in the engine. A small surface that can be checked
exhaustively beats a large one that must be trusted. That is a trade, not a free win.

### Why that thread is virtual

Confinement is the *correctness* argument; it says nothing about what a thread should cost.
The first implementation used a platform thread per game
(`Executors.newSingleThreadExecutor` with a `new Thread(...)` factory). That reserves ~1 MB of
stack per game, whether or not the game is doing anything — and a game actor is idle between
ticks, which at a 500 ms tick and a ~1 ms round is **99.8 % of the time**. The server was
paying its largest per-game cost for a thread that is almost always parked.

The fix was one factory:

```java
// before — a platform thread per game
new Thread(runnable, "game-" + gameId)
// after — a virtual thread per game
Thread.ofVirtual().name("game-" + gameId).factory()
```

`newSingleThreadExecutor` still runs one task at a time, so **the confinement argument above is
untouched** — the engine still needs no locks, and the naming still identifies the game in a
stack trace. Only the price changes: a few hundred bytes of heap instead of a megabyte of
stack, and the carrier thread is released the moment the actor parks.

The decisive point for the report: **thread count stops being the ceiling on hosted games.**
CPU is unaffected — 1000 games × 1 ms round / 500 ms tick ≈ 2 cores of work, comfortable on 8 —
so the platform-thread build died of thread exhaustion long before the CPU was the issue.

### The three mechanisms considered, and why virtual won

| Mechanism | Per game | Rejected because | Evidence |
|---|---|---|---|
| **Platform thread per game** (dedicated) | ~1 MB stack | hard wall: `OutOfMemoryError: unable to create native thread` | **measured** — memory/thread curve, plus a `ulimit -u` run that fails at a chosen N |
| **Per-game lock + shared pool** (pooled) | ~48 B lock | a round that blocks holds a pool thread **and** the game's lock → pool starvation. Safe only under a "never block in a round" discipline the compiler cannot enforce | **reasoned** (optionally: an 8-thread pool, 8 blocking tasks, a 9th that never runs) |
| **Virtual thread per game** ✅ | ~few hundred B | — | **measured** — flat line on the same curve |

Two notes that keep the comparison honest:

- The memory curve rejects *dedicated*, **not** *pooled* — pooled would look excellent on it
  (few threads, flat memory). The two fail for different reasons and need different evidence.
- Pooled is the one design where a **watchdog** would earn its place: sample stuck tasks,
  `getStackTrace()` the offending thread, migrate or quarantine the session. That machinery
  exists to protect a weakness virtual threads do not have, which is precisely the argument
  against building it.

Rejected earlier, and for the record: one coarse `synchronized` (correct, but serialises
unrelated games), fine-grained locks (deadlock risk as an operation walks board → piece →
player), and immutable/copy-on-write state (elegant, but a rewrite of the engine Assignment 1
already tested).

### Ticking

Rounds are **not** run in a loop with a sleep, which would hold the actor thread and make the
session deaf to pause and abort. Each completed round schedules the next on a shared
`ScheduledExecutorService`; between rounds the actor is idle and free to serve commands.
Self-scheduling also means a round that overruns its interval delays the next rather than
overlapping with it.

### The two queues, and why both are bounded

**Inbound (`RequestQueue`)** — bounded `ThreadPoolExecutor` over an `ArrayBlockingQueue`. When
full, the saturation policy **runs the task on the calling thread**. Two consequences, both
wanted: no request is ever silently lost, and the connection thread that submitted it is busy
for the duration, so it stops reading that socket and the flooding client is throttled at
source. Backpressure, not data loss.

**Outbound (per `ClientConnection`)** — bounded; when full the **oldest** pending frame is
dropped. A client that has fallen behind wants the current board, not a backlog of superseded
ones.

An unbounded queue does not remove a limit — it converts a slowdown into an out-of-memory
failure.

### Why pushes never block a game

Pushes originate on *session* threads. If those wrote to sockets directly, a client that
stopped reading would block the game loop trying to notify it, and that game would freeze for
**every** viewer. Instead a session drops the event in the connection's queue and returns.

### Composition root

`server/ServerAssembly` is the single place dependencies are constructed — which is why no
interactor and no engine class builds its own collaborators or reaches for a static singleton.
It is shared by `ServerMain` and the integration tests, so the tests exercise the same
assembly the tutor will see running.

Each inner component receives its outer collaborator disguised as a port it declared itself:
`SessionRegistry` receives the `ConnectionRegistry` but sees only an `EventSink`.

---

## 6. Step 4 — the Swing GUI

Vanilla Swing. Production `src/` imports **nothing but the JDK** — no JSON library, no Netty,
no DI framework. The only jars in `lib/` are JUnit and JaCoCo, which never reach the server or
client classpath.

### What the window is for

**Nobody plays the pieces.** The four AI strategies drive every game, exactly as in Assignment
1. This is an operator's control room: create games, run/pause/step them, watch several at
once. Every button is a request; every change on screen comes from a push, including changes
another client caused.

### Components

| Class | Role |
|---|---|
| `client/ServerConnection` | async request-reply + push stream |
| `client/ClientMain` | entry point, connect, error dialog |
| `ui/MainFrame` | layout, event routing, shortcuts, reconnect |
| `ui/BoardCanvas` | custom `Graphics2D` board with animation |
| `ui/BoardGeometry` | cell index → 15×15 grid square |
| `ui/LobbyPanel` | sortable, live-updating session table |
| `ui/ControlBar` | start/pause/resume/step/abort + speed |
| `ui/PlayerStatusPanel` | per-player counts, captures, effects |
| `ui/EventLogPanel` | colour-coded feed, capped, follow toggle |
| `ui/MetricsStrip` | queue gauge, workers, clients, saturation |
| `ui/Theme` | all colours/fonts, dark + light |

### Client threading — why the GUI never freezes

- **EDT** paints and handles input. It never reads or writes a socket.
- **Sender thread** owns writes. `send()` hands the frame over and returns, so a click cannot
  block the UI even if the network stalls mid-write.
- **Receiver thread** owns reads and blocks permanently — which is exactly what makes pushes
  appear the moment they arrive. **There is no polling anywhere in this client.**

Replies arrive in any order, so each request parks a `CompletableFuture` in a
`ConcurrentHashMap` keyed by request id, and the receiver completes the right one. That is
what lets the UI keep many requests in flight without waiting.

`ServerConnection` marshals listeners onto the EDT via `SwingUtilities.invokeLater` **itself**,
so a UI component physically cannot touch a Swing model from the receiver thread — the classic
way Swing applications corrupt themselves.

> When chaining off a request, use `thenAcceptAsync(action, SwingUtilities::invokeLater)`.
> A plain `thenAccept` runs on the receiver thread and touches Swing off the EDT.

### Board geometry

The 52-cell ring is one 13-cell arm repeated four times, each rotated a quarter turn by
`(x,y) → (14-y, x)`. Generating it rather than typing 52 coordinates means the arms cannot
drift out of symmetry.

The numbering in `GameConstants` then lines up **for free**, which is strong evidence the
constants describe this exact layout:

| Constant | Value | Lands on |
|---|---|---|
| `YELLOW_START` / `BLUE_START` / `RED_START` / `GREEN_START` | 0 / 13 / 26 / 39 | the four arm entries, each beside its own yard |
| `YELLOW_APPROACH` / `BLUE_APPROACH` / `RED_APPROACH` / `GREEN_APPROACH` | 50 / 11 / 24 / 37 | directly outside their own home straight |
| `ALPHA` / `BETA` / `GAMMA` | 7 / 25 / 44 | marked α, β, γ |

Grid coordinates only — no pixels — so the board stays sharp at any window size.

### Why pieces animate

Snapshots arrive one per round, so a piece moving six cells would teleport, and with four
players moving each round it becomes impossible to see *what happened* — only what is now
true. Interpolating between the last two snapshots (260 ms, ease-out) turns each round into a
movement you can follow. Presentation only: it never changes what the board reports, and a
snapshot arriving mid-animation simply becomes the new target.

Pieces sharing a square fan out around its centre, so a stack of four still reads as four.

### Features

Lobby (sortable, live, selection-preserving) · new-game dialog (mode/seed/speed) · start ·
pause · resume · step · abort · speed slider · animated board · mystery-cell pulse · effect
outlines · cell tooltips · player status with effects · colour-coded capped event log with
follow toggle · server metrics with queue gauge · dark/light toggle · connection indicator ·
auto-reconnect with re-subscribe · non-blocking toasts · keyboard shortcuts.

| Shortcut | Action |
|---|---|
| `Ctrl+N` | New game |
| `Ctrl+R` | Start |
| `Ctrl+P` | Pause / Resume (chosen from state) |
| `Ctrl+.` | Step one round |
| `Ctrl+D` | Toggle theme |
| `F5` | Refresh lobby |

### Two layout lessons worth keeping

1. **`setDividerLocation` is ignored until a `JSplitPane` has been validated.** Setting it in
   `windowOpened` was silently dropped, the board took the whole width, and the right-hand
   column collapsed to zero. Fixed by using `BorderLayout` for the horizontal arrangement —
   WEST/EAST always get their preferred width.

2. **Windows commonly runs at 125% scaling.** A 1536-pixel screen offers ~1229 logical pixels,
   so two 320-wide side panels plus a board do not fit. Side panels are now 260 and the window
   sizes itself against the actual desktop. `MainFrame.reportLayout()` prints the resulting
   widths on startup — side panels being squeezed off-screen is invisible in code review and
   obvious in one line of output:

   ```
   [ui] frame=1166 lobby=260 board=646 side=260 screen=1536 scale=1.25
   ```

---

## 7. Design patterns

Reusing Assignment 1's patterns is the *stronger* position: the Architecture criterion is
about preserving Clean Architecture through the refactor, and the best evidence is those
patterns still standing in place with a network under them.

| Pattern | Where | Note |
|---|---|---|
| **Observer** | `GameEventPublisher` → `GameEventListener` → `Logger` **and** `GameEventBroadcaster` | the port that made the refactor cheap |
| **Façade** | `GameEngine` | now driven by interactors |
| **Builder** | `GameEngineBuilder` | extended with `withRandomSource(...)` |
| **Strategy** | `PieceSelectionStrategy` ×4 | untouched — still drives every game |
| **DTO** | `MoveResult`; `shared.*` records | internal → wire, same pattern |
| **Factory Method** | `PlayerFactory` | unchanged |
| **Template Method** | `AbstractPlayer` | unchanged |
| **State** | `PieceState` | unchanged |
| **Null Object** | `NoPiece` | unchanged |
| **Singleton** | `NoPiece` only | ⚠ see below |
| **Actor** | `GameSession` | one thread owns one game |
| **Adapter / Gateway** | `SnapshotMapper`, `ServerEventMapper` | the only both-vocabulary classes |
| **Proxy** | `ClientConnection` | controllers call `ClientSession`, never a socket |
| **Object Pool** | `RequestQueue` worker pool | (H2 connection pool in step 5) |
| **Composition Root** | `ServerAssembly` | makes every layer substitutable |
| **MVC-ish** | `ui/` reads DTOs, sends commands | no game state in the view |

> ⚠ **Do not quietly drop the Singleton row.** "We applied Singleton, discovered it was a
> correctness bug under concurrency, and replaced it with an injected per-game `RandomSource`"
> is a far better answer than a clean table — and it is the same worked example the critical
> discussion needs.

---

## 8. Running it

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1        # compile src/ into out/
powershell -ExecutionPolicy Bypass -File run-server.ps1   # server tier
powershell -ExecutionPolicy Bypass -File run-client.ps1   # GUI client
powershell -ExecutionPolicy Bypass -File run-client.ps1 -ServerHost 192.168.1.20  # 2nd machine
```

> `run-tests.ps1` compiles only `test/` and assumes `out/` is current. **Run `build.ps1`
> first**, or a stale `out/` silently tests old code.

Server flags: `--port=5599 --workers=4 --queue=256 --metrics=1000`. Small worker/queue values
make the queue visibly fill during the load demonstration.

### Headless client for scripted demos

`client.SmokeClient` is a keyboard-driven client, deliberately built the same shape as the GUI
client. It supports `sleep <millis>` so a demo can be written to a file and replayed
identically:

```
create LUDO_T 42 150
sleep 2000
sub g1
start g1
sleep 4000
pause g1
```

```powershell
java -cp out client.SmokeClient localhost 5599 < demo.txt
```

---

## 9. Testing

**172 tests, all passing** (138 original + 34 new).

| Suite | Guards |
|---|---|
| `RandomSourceIsolationTest` | per-game RNG isolation (fails on pre-fix code) |
| `ProtocolCodecTest` | handshake deadlock, stream reset, ordering, type errors |
| `RequestQueueTest` | nothing dropped under saturation; backpressure recorded |
| `GameSessionTest` | lifecycle, snapshot consistency, concurrent independence |
| `RequestRouterTest` | whole adapter+app stack **with no socket anywhere** |
| `ServerPushIntegrationTest` | two clients over real sockets; push visibility; burst load |

### Verified live

- Two processes, one client creates + pauses, another subscribes + starts → the second
  received **all 19 rounds unprompted** plus the `PAUSED` state change.
- GUI **does not** create anything on its own: game list empty before launch and still empty
  12 s after, with the window open.

### Three defects the live run caught that tests had not

1. `subscribers` always 0 — the controller updated the connection's routing set but never the
   session's viewer count, leaving `GameSession.addSubscriber` dead code.
2. Viewer counts leaked on disconnect — a crashed client inflated a game's count permanently.
3. `SmokeClient` printed a stack trace on disconnect instead of a clean message.

All three only appeared by **running the thing**. That is the argument for building the GUI
against a server that already worked.

---

## 10. Outstanding

| Step | Work |
|---|---|
| 5 | `persistence/` + H2 as its own process; `db/schema.sql`, `db/seed.sql`; async writer; leaderboard/history tabs. `GameRepository.NO_OP` is the seam — swap one argument in `ServerMain`. |
| 6 | `client.testing.TestClientMain` + `run-testclients.ps1` — N headless clients × M threads, latency percentiles, observed queue depth. |
| 7 | Report: (a) Clean Architecture across tiers, (b) critical analysis of concurrency mechanisms, with the RNG singleton as the worked example and §5 "the three mechanisms considered" as the threading example. |
| 7b | Benchmark harness: hosted-games sweep at 10 / 100 / 500 / 1000 recording thread count + memory, run against both the platform-thread build (via git history) and the virtual build, plus one `ulimit -u 500` hard-fail run. Builds on the step-6 test clients. |

### Rubric map (from `docs/Assignment 2 - Brief (1) (1).pdf`)

| Criterion | Weight | Top band needs | Status |
|---|---|---|---|
| Quality of the GUI | 10% | many features integrated | done |
| Server-side concurrency | 25% | simultaneous requests from fast automatic clients, queued | `RequestQueue` done; **needs step 6 clients to evidence** |
| Client-side concurrency | 25% | **two test clients** sending async requests in rapid succession | **step 6, not built** |
| Architecture | 20% | client, server, database as **three different applications** | **step 5, not built** |
| Critical discussion | 20% | *all* appropriate mechanisms discussed with insightful justification | §5 covers the threading set; breadth is the grade ladder, so keep rejected mechanisms in |

Priority follows the weights: steps 6 and 5 gate the top band on 45%, and step 6 also
generates the load numbers step 7b needs. Threading discussion is already written.

### Checkpoint

The brief requires **showing the interface to your tutor before continuing**. That is here.
