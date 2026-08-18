# Ludo-T — Design Rationale & Evidence Ledger

**Purpose of this document.** `IMPLEMENTATION-NOTES.md` records *what* was built.
This document records **how each decision was reached, what evidence supports it, and what it
costs** — written so that another agent (or the author, months later) can pick the project up
without re-deriving anything.

It is also the assembly source for the report's section (b): *critically analyse the
concurrency mechanisms available and justify your choice*.

---

## 0. How to read this document

Three rules govern everything below, and an agent working from it must preserve them.

**Rule 1 — every claim carries an evidence label.**

| Label | Meaning |
|---|---|
| 🟢 **MEASURED** | A number produced by running code. Reproducible by a named command. |
| 🟡 **DERIVED** | Arithmetic from measured or documented inputs. The inputs are stated so the derivation can be checked. |
| 🔵 **REASONED** | A structural argument. No number. Legitimate, but must never be presented as measurement. |
| 🔴 **PENDING** | Not yet established. A slot with a shape, not a result. |

**Rule 2 — 🔴 PENDING slots must never be filled with plausible-looking numbers.**
This is an assessed submission with a demonstration. Inventing benchmark results would be
academic misconduct, and a tutor who asks "how did you get that?" will find out. Run the
benchmark in §9, or present the slot as open.

**Rule 3 — rejected options are recorded as fully as the chosen one.**
The rubric's top band for Critical discussion reads "*all* appropriate concurrency mechanisms
discussed with insightful justification". An option that was considered and rejected earns
marks only if it is written down. See §10 for options deliberately cut — do not re-propose them
as fresh ideas.

---

## 1. What the assignment actually rewards

Module COMP63038, Assignment 2, 50% of module marks. Source:
`docs/Assignment 2 - Brief (1) (1).pdf`.

**Learning outcomes:**
1. *Apply* concurrency in a multiuser, multi-tier, client-server application.
2. *Critically discuss* the concurrency mechanisms available and **justify the selection made**.

LO2 is worth noticing: "justify the selection made" presupposes that some mechanisms were
selected and others rejected. Breadth of discussion is the deliverable, not breadth of
implementation.

### Rubric map — where the marks are

```
  Server-side concurrency  ████████████████████████░  25%   queue under simultaneous fast clients
  Client-side concurrency  ████████████████████████░  25%   two async test clients, live cross-client update
  Architecture             ███████████████████░░░░░░  20%   client / server / DB as three applications
  Critical discussion      ███████████████████░░░░░░  20%   ALL mechanisms, insightful justification
  Quality of the GUI       █████████░░░░░░░░░░░░░░░░  10%   many features integrated
```

| Criterion | Weight | Top band requires | Status |
|---|---|---|---|
| Quality of the GUI | 10% | many features integrated | ✅ done |
| Server-side concurrency | 25% | multiple clients, simultaneous, **queued** | ⚠️ `RequestQueue` built; needs test clients to *evidence* |
| Client-side concurrency | 25% | **two test clients**, async, rapid succession | ❌ not built (step 6) |
| Architecture | 20% | client, server, database as **three applications** | ❌ DB tier not built (step 5) |
| Critical discussion | 20% | *all* mechanisms, insightful justification | ✅ this document + §5 of notes |

**Consequence for prioritisation.** 45% of the marks are gated by two unbuilt steps. The
threading analysis below is already complete and serves the 20% criterion; it should not
consume more build time. Order: **test clients → database tier → benchmark harness**.

---

## 2. Current state

```
┌─────────────┐   Swing GUI      ┌─────────────┐   sockets    ┌──────────────┐
│  client/ui  │ ───────────────▶ │   server    │ ───────────▶ │  H2 database │
│  (process)  │ ◀─────────────── │  (process)  │              │  (process)   │
└─────────────┘   server pushes  └─────────────┘              └──────────────┘
      ✅ built                        ✅ built                    ❌ step 5
```

🟢 **MEASURED:** `powershell -File run-tests.ps1` → **172/172 tests pass**, 28 containers,
2.5 s. `build.ps1` → 123 production files compile. JDK: Temurin 25.0.1.

⚠️ `coverage.ps1` reports 277/284 — 7 phantom failures from stale class files in `out\test\`
and `out\coverage-test\` (an old `test.test.*` package layout compiled against a superseded
`EffectHandler` constructor). Pre-existing, unrelated to any design decision here, fixable with
two `Remove-Item` lines. **Do not cite the 284 number anywhere.**

---

## 3. Decision 1 — killing the RNG singleton 🟢

This is the strongest evidence in the whole project, because it is a real bug caught by
concurrency and fixed with before/after numbers.

**Forcing constraint.** `RandomInitiator` held a process-wide mutable `Random`; `Dice` was a
singleton over it; `GameEngineBuilder.build()` called `setSeed()` on it — a global side effect.
Correct single-threaded. Broken the moment one process hosted several games.

**Two distinct failures:**
1. Creating a seeded game **reseeded every game already running**.
2. Concurrent games **consumed each other's number stream** — seeded runs stopped being
   reproducible.

**The insight worth stating in the report.** `Random.nextInt` *is* thread-safe. Thread safety
was never the problem. **Shared mutable state** was. Synchronising harder would not have fixed
a single one of these failures — the fix was removing the sharing.

**The fix.** A `model/RandomSource` port threaded per-game:

```
GameEngineBuilder → GameEngine → TurnManager        → Dice
                               → EffectHandler      (coin toss, alpha effect)
                               → MysteryCellManager → MysteryCell
```

Every old constructor kept as a delegating overload, so all 18 original test classes compiled
unchanged.

**🟢 MEASURED evidence** — `test/RandomSourceIsolationTest.java`, verified by stashing the fix
and re-running:

| Scenario | Before | After |
|---|---|---|
| Seeded game alone | 970 rounds | 970 ✓ |
| Another seeded game built mid-run | **928** ❌ | 970 ✓ |
| 8 same-seed games run concurrently | **633** ❌ | 970 ✓ |

```
rounds
 1000 ┤ ●970        ●970        ●970       ← after fix: flat, deterministic
      │
  900 ┤             ○928
      │
  800 ┤
      │
  700 ┤
      │                         ○633       ← before fix: degrades with concurrency
  600 ┤
      └──────────────────────────────────
        alone      +1 game     8 games
```

**Why this belongs in the report.** It converts an abstract objection to singletons into a
number a tutor can watch reproduce. It is also the cleanest possible illustration of the
report's central distinction: *thread-safe primitives do not make a design concurrent-safe.*

---

## 4. Decision 2 — actor confinement instead of locks 🔵

**Forcing constraint.** `GameEngine` is a deep graph of mutable objects — board → cells →
pieces → players → turn manager — and one move reaches across all of it. A capture reads a
cell, mutates the moving piece, mutates a *different player's* piece, then mutates turn state.

### Options considered

```
┌──────────────────────────┬───────────────────────────────────────────────────┐
│ A. One global lock       │ Correct. Every game waits for every other game.   │
│                          │ → a multi-user server that plays ONE GAME AT A    │
│                          │   TIME. Passes tests, defeats the assignment.     │
├──────────────────────────┼───────────────────────────────────────────────────┤
│ B. One lock per game     │ THE LEGITIMATE RIVAL — not a strawman.            │
│                          │ Same mutual exclusion, same correctness.          │
│                          │ Loses on two specific points (below).             │
├──────────────────────────┼───────────────────────────────────────────────────┤
│ C. Fine-grained locks    │ Real parallelism inside a game. Deadlock risk,    │
│    (per piece / cell)    │ and it retracts the project's central claim.      │
├──────────────────────────┼───────────────────────────────────────────────────┤
│ D. Immutable / CoW state │ Genuinely elegant. Requires rewriting the engine  │
│                          │ as a pure Round → GameState function — i.e. a     │
│                          │ rewrite of the code Assignment 1 already tested.  │
├──────────────────────────┼───────────────────────────────────────────────────┤
│ E. Concurrent collections│ NON-SOLUTION. A thread-safe container does not    │
│    inside the engine     │ make a multi-step capture atomic. Looks like a    │
│                          │ fix, is not one. Worth stating for exactly that   │
│                          │ reason.                                           │
├──────────────────────────┼───────────────────────────────────────────────────┤
│ F. Actor confinement ✅  │ CHOSEN.                                           │
└──────────────────────────┴───────────────────────────────────────────────────┘
```

### Why B loses — the two specific points

**1. The caller waits.** With a lock, a request-worker thread holds it for the whole round.
With the actor, `submit()` posts a job and returns immediately. A slow round under B propagates
backwards: worker threads block → `RequestQueue` saturates → CallerRuns throttling fires. Under
F the queue depth is just a number.

**2. Ordering is not guaranteed.** `ReentrantLock` is unfair by default: a `pause` request has
no guaranteed position and can be repeatedly jumped by tick acquisitions. A fair lock fixes
this and costs throughput. **A mailbox is FIFO by construction** — the actor gives ordering
free, which no lock does.

### Why C is worse than it looks *in this specific codebase*

`GameEventBroadcaster` is registered as a `GameEventListener`, so **the engine calls out to
adapter code in the middle of `executeRound()`**, and that code reaches `EventSink` →
connection queues.

```
        ┌─ holding a per-piece lock ─┐
engine.executeRound()                │
   └─▶ publisher.publishCapture()    │   ← calls OUT of the domain,
          └─▶ GameEventBroadcaster   │      into code the engine
                └─▶ EventSink        │      does not control
                      └─▶ queues ────┘
```

Holding a lock while invoking code you do not control is the canonical deadlock recipe: lock
ordering would come to depend on adapter behaviour the domain cannot see. Add that a capture
touches three objects in an order every future change must respect, and the invariant is
enforced by nothing but memory.

**The decisive objection is architectural.** Locks under C live *inside* the engine, so every
engine method needs annotation — which retracts the project's central claim:

> *The game rules did not change. All original tests still pass untouched.*

Confinement is what makes that claim true. 🟢 MEASURED: 172/172 pass, zero `synchronized` in
`src/engine` or `src/model`.

### The line that carries the argument

> **A lock makes shared access safe. Confinement makes access not shared at all.**
> When the protected thing is a deep mutable graph that already calls out to listeners, the
> second is cheaper to prove correct — and it is the only one that leaves the Assignment 1
> engine untouched.

---

## 5. How the actor works — the mechanism

An actor is three things:

```
        ┌─────────────────────────────────────────────────┐
        │                  GameSession g1                 │
        │                                                 │
        │   MAILBOX              WORKER          STATE    │
        │   ┌───────────┐      ┌──────────┐   ┌─────────┐ │
   ───▶ │   │ job │ job │ ───▶ │ game-g1  │──▶│  Game   │ │
  post  │   │  3  │  2  │      │ (virtual │   │ Engine  │ │
        │   └───────────┘      │  thread) │   └─────────┘ │
        │    FIFO queue        └──────────┘   owned, not  │
        │  (single-thread          one at      shared     │
        │    executor)             a time                 │
        └─────────────────────────────────────────────────┘
                    ▲
                    └── callers get a CompletableFuture back — a RECEIPT, not a result
```

| Part | What it actually is in the code |
|---|---|
| mailbox | the queue inside `Executors.newSingleThreadExecutor` |
| worker | the virtual thread named `game-g1` |
| owned state | that session's `GameEngine` |

`submit()` / `supply()` (`GameSession.java:294`) post the job and return a `CompletableFuture`
immediately. The executor runs jobs **one at a time, in the order posted**.

> **One worker + one queue = mutual exclusion *and* ordering.** A lock gives only the first.

### A Pause click, traced through five threads

```
 [1] client sender ──▶ writes frame to socket
                          │
 [2] client-reader ◀──────┘  decodes, enqueues into RequestQueue
                          │
 [3] request-worker ◀─────┘  routes → PauseGameUseCase → session.pause()
                          │     posts job, takes receipt, MOVES ON  ◀── key moment
                          │
 [4] game-g1 (actor) ◀────┘  runs the job ──▶ TOUCHES THE ENGINE  ◀── the ONLY engine access
                          │
 [5] client-writer  ◀─────┘  pushes resulting event back to every subscriber
```

**Five threads handled one request. Exactly one went near the engine.** Threads 1, 2, 3 and 5
never had access to guard in the first place — which is why there is nothing to lock.

### Ticking obeys the same rule

The scheduler thread does **not** run the round (`GameSession.java:229`):

```java
scheduler.schedule(() -> submit(this::runTick), tickMillis, MILLISECONDS);
```

It posts a job and leaves. Between ticks the mailbox is empty and the actor is parked. Two
consequences: a `pause` arriving mid-round is simply *the next letter* rather than an
interruption, and a round that overruns its interval delays the next rather than overlapping
with it.

---

## 6. What multiple users actually experience

### Different games — genuinely parallel

```
Alice ──▶ [mailbox g1] ──▶ actor g1 ──▶ engine g1  ─┐
Bob   ──▶ [mailbox g2] ──▶ actor g2 ──▶ engine g2  ─┼── all at the same time
Carol ──▶ [mailbox g3] ──▶ actor g3 ──▶ engine g3  ─┘   nobody waits
```

### The same game — one queue, arrival order

```
Alice: pause ─┐
Bob:   step  ─┼──▶ [ ONE mailbox ] ──▶ actor g1 ──▶ engine
Carol: speed ─┘         FIFO
```

This is a **feature, not a limitation**. Several users act on one shared game, so there must be
one order. Alice's pause and Bob's step cannot both half-happen — the queue decides who was
first, and both receive a definite answer.

### Then everyone is told

```
actor g1 ──▶ EventSink ──▶ ┌─▶ Alice's connection queue ──▶ writer ──▶ Alice's screen
                           ├─▶ Bob's   connection queue ──▶ writer ──▶ Bob's screen
                           └─▶ Carol's connection queue ──▶ writer ──▶ Carol's screen
```

Bob's step appears on Alice's screen without her asking — **the client-side rubric's top
band**: *"when one client changes data that is displayed on another client, the other client
will immediately show the change."*

### A hundred requests at once

```
100 requests ──▶ ┌────────────────────────┐
                 │ RequestQueue           │  bounded ArrayBlockingQueue (cap 256)
                 │ ████████████░░░░░░░░░  │  4 workers by default
                 └────────────────────────┘
                            │
                   queue full? ──▶ CallerRuns: the FLOODING CONNECTION does the work itself
                                    → that client stops reading its socket
                                    → it throttles ITSELF, not everyone
                                    → nothing is ever dropped
```

**Server-side rubric top band**: *"handle multiple requests from multiple clients, sent
simultaneously by fast automatic clients, by putting them in a queue."*

Design note worth defending: `ServerConfig` defaults are deliberately *small* — 4 workers,
queue 256. A server sized to absorb any burst would never demonstrate a queue at all.

---

## 7. Decision 3 — virtual threads for the actor 🟡

Confinement is the *correctness* argument. It says nothing about what a thread should **cost**.

**Forcing constraint.** The first implementation used a platform thread per game. A platform
thread reserves ~1 MB of stack whether or not it is doing anything.

### The idle-duty derivation 🟡

```
INPUTS
  tick interval   = 250 ms      🟢  LobbyController.DEFAULT_TICK_MILLIS
  round duration  = ~1 ms       🔴  ASSUMED — NOT YET MEASURED. See §9.

DERIVED
  duty cycle      = 1 / 250            = 0.4 %
  idle fraction   = 99.6 %             ← the actor is parked almost always
  CPU for N games = N × 1ms / 250ms    = N / 250 cores
                    1000 games         = 4 cores        (comfortable on 8)
```

**⚠️ The round duration is an assumption, not a measurement.** It is the input the whole
derivation rests on, so §9 measures it first. If a round turns out to take 10 ms, the CPU
figure becomes 40 cores at 1000 games and the conclusion changes — CPU, not memory, would
become the binding constraint.

### The change

```java
// before — a platform thread per game
new Thread(runnable, "game-" + gameId)
// after — a virtual thread per game
Thread.ofVirtual().name("game-" + gameId).factory()
```

`newSingleThreadExecutor` still runs one task at a time, so **§4's confinement argument is
untouched.** Naming survives, so stack traces still identify the game. Virtual threads are
always daemons, so the old `setDaemon(true)` became redundant.

🟢 MEASURED: after the change, 172/172 tests still pass; `javac --release 23` still compiles,
so the JaCoCo path is unaffected.

### The three threading mechanisms compared

| | **A. Platform thread/game** | **B. Per-game lock + shared pool** | **C. Virtual thread/game** ✅ |
|---|---|---|---|
| Cost per game | ~1 MB stack | ~48 B lock | ~few hundred B heap |
| Threads at 1000 games | ~1000 OS threads | ~8 OS threads | ~8 carriers |
| Confinement preserved | ✅ | ✅ (via the lock) | ✅ |
| Engine needs locks | ❌ no | ❌ no | ❌ no |
| Deadlock risk | none | none — **one lock per game, nobody holds two, so a cycle is structurally impossible** | none |
| Fails by | `OutOfMemoryError: unable to create native thread` | **pool starvation** if a round blocks | heap exhaustion (far later) |
| Requires discipline | no | **yes — "never block in a round", uncheckable by the compiler** | no |
| Evidence | 🔴 PENDING benchmark | 🔵 REASONED | 🔴 PENDING benchmark |

### Expected shape of the result 🔴

**This is a hypothesis, not a result. Do not present it as data.**

```
memory
   ▲
   │                                        ╱  A: platform threads
   │                                      ╱     (linear, hits a hard wall)
   │                                    ╱
   │                                  ╱  ✖ OOM: unable to create native thread
   │                                ╱
   │                              ╱
   │                            ╱
   │  ─────────────────────────────────────  C: virtual threads (near-flat)
   └──────────────────────────────────────▶
      10      100      500     1000    games
```

### Two things that keep the comparison honest

**1. The memory curve rejects A, NOT B.** B would look *excellent* on this graph — few threads,
flat memory. A and B fail for entirely different reasons and need entirely different evidence.
Presenting one graph as rejecting both is the mistake a tutor will catch.

**2. B's weakness needs its own demonstration.** Pool starvation:

```
pool of 8 threads
  ├─ task 1 blocks (slow EventSink) ─┐
  ├─ task 2 blocks                   │  all 8 carriers held,
  ├─ ...                             │  AND 8 game locks held
  └─ task 8 blocks ──────────────────┘
     task 9 ──▶ never runs. 992 other games frozen.
```

Optional 20-line proof: a fixed pool of 8, 8 blocking tasks, a 9th that never runs. Not an
implementation of B — just evidence for the failure mode being cited.

**3. B is the one design where a watchdog would earn its place** — sample stuck tasks,
`getStackTrace()` the offending thread, migrate or quarantine the session. That machinery
exists to protect a weakness C does not have, **which is the argument against building it.**

### The point that makes the decision

**Thread count stops being the ceiling on hosted games.** CPU was never the constraint at demo
scale (4 cores of work at 1000 games), so the platform build would die of thread exhaustion
long before the CPU mattered. Virtual threads remove the binding constraint and leave the
correctness argument completely intact — in one line.

---

## 8. Constraints accepted — the honest list

**Volunteer these. Do not let them be discovered.** The brief states: *"if asked, you must be
able to explain in detail the software you present."* A weakness raised by the author reads as
mastery; the same weakness found by the assessor reads as a gap.

### 8.1 Reads queue behind writes 🔵

`snapshot()` waits behind a running round. A `ReadWriteLock` would let readers run
concurrently. Cheap here — rounds are short — but a genuine cost of the design.

### 8.2 Confinement is already broken in one place, deliberately 🟢

`summary()` (`GameSession.java:202`) reads `engine.getRoundNumber()` from **any** thread, off
the actor. That is a real data race.

- **Why it was accepted:** a stale round number in a lobby listing harms nobody, and routing
  every listing through every game's actor would let one slow game delay the whole lobby.
- **Why it must be stated first:** it is the one crack in "nothing is shared". The code's own
  javadoc already documents it as deliberate. Saying it before being asked demonstrates the
  invariant is understood rather than assumed.

### 8.3 Nothing can be atomic across two games 🔵

Confinement gives exclusivity per game and no way to hold two at once. A consistent read of two
boards, or moving a player between games, has no clean answer. Per-game locks could take both
(in a fixed order). Irrelevant to Ludo — but it is the design's real structural limit.

### 8.4 Deadlock is not gone, only disguised 🔵

**"Actors cannot deadlock" is false.** If actor A blocks on actor B's future while B waits on
A's, the server hangs with no lock to point at.

```
actor g1 ──join()──▶ future of g2
   ▲                      │
   └──────join()──────────┘        ← deadlock, and no lock exists to blame
```

Concretely: calling `.join()` on another session's future from inside an actor task. This code
never does — but the hazard is structural, not absent.

### 8.5 Everything becomes asynchronous 🔵

`CompletableFuture` through every use case, stack traces split across threads, harder
debugging. Paid in every class in `app/usecase`.

### The summary that earns the marks

> Confinement does not remove concurrency problems. It **concentrates** them — here into a
> handful of `volatile` fields and the event sink, instead of smearing them across every method
> in the engine. A small surface that can be checked exhaustively beats a large one that must
> be trusted. **That is a trade, not a free win.**

---

## 9. The benchmark plan — how to fill the 🔴 slots

Nothing in this section has been run yet. It is specified so that whoever runs it produces
numbers that can be defended.

### 9.1 Measure round duration first — everything depends on it

The 1 ms figure in §7 is **assumed**. It is the input to the duty-cycle and CPU derivations, so
measure it before quoting either.

```
Method: time engine.executeRound() across ≥1000 rounds in a single session,
        report median and p99, per game mode.
Output: a real number replacing 🔴 in §7.
```

### 9.2 The scaling sweep

| Games | Record | Both builds |
|---|---|---|
| 10, 100, 500, 1000 | OS thread count, RSS, heap used, tick jitter (scheduled vs actual) | A = platform (from git history), C = virtual |

Plot memory vs games. Expected: A linear, C near-flat. **Stop before A crashes** — the
extrapolated wall is better evidence than one screenshot of an exception, and it never risks
the machine.

### 9.3 The exhaustion demo — constrain the environment, don't buy a bigger one

Thread exhaustion is **easier** to show with *less* memory. Two safe, reproducible options:

```bash
# WSL — hard cap on threads. A fails at exactly 500. C keeps going.
ulimit -u 500
java -cp out server.ServerMain

# Docker — the CONTAINER dies, not the laptop.
docker run --rm -m 256m -v "$PWD":/app -w /app eclipse-temurin:25 \
  java -cp out server.ServerMain
```

Both fail at a number *chosen in advance*, in seconds, identically every time.

**For the live demonstration: stay at 50–100 games.** Never risk a hang in front of the tutor.
Exhaustion belongs in the report as measured data, with the constrained run available on
request as a 10-second, by-design failure.

### 9.4 Free environments, if a bigger machine is wanted

| Option | Free tier | Note |
|---|---|---|
| Google Cloud Shell | free, no card | easiest; browser terminal, `ulimit` works |
| GitHub Codespaces | ~60 h/month | 2-core/8 GB, Docker available |
| **GitHub Actions** | 2000 min/month | 4-core/16 GB — **the run log is timestamped, public, reproducible evidence** |
| Oracle Cloud Always Free | 24 GB ARM VM | largest, requires a card |

GitHub Actions is the strongest for a report: the benchmark becomes a committed job whose log a
tutor can re-run.

### 9.5 Depends on step 6

The load numbers come from the test clients (`client.testing.TestClientMain`). Building those
first serves the 25% client-side criterion *and* produces this section's inputs. **Do not build
a separate benchmark harness before the test clients exist.**

---

## 10. Deliberately cut — do not re-propose

Each of these was worked through in detail and rejected **on purpose**. An agent picking this
up should treat them as closed unless the author reopens them.

| Cut | Why |
|---|---|
| **POOLED implementation** (per-game lock + shared pool) | Discussed, not built. Rubric rewards mechanisms *discussed*; building it costs days that steps 5–6 need. |
| **`SessionExecutor` strategy interface + 3 implementations** | Considered at length. Only VIRTUAL ships; DEDICATED survives in git history as the before/after. |
| **Watchdog** (stack-trace sampling of stuck tasks) | Existed to protect POOLED. Genuinely useful as a *dev tool* to find blocking calls during load testing — but not a production feature, and not a mechanism to defend. |
| **Supervisor / auto-migration** between executor strategies (interrupt → grow pool → quarantine) | Real engineering, but a recovery mechanism for a problem virtual threads do not have. Saying so is stronger than building it. |
| **Fine-grained locks, immutable/CoW state, global lock** | Rejected on the merits — see §4. Recorded, not built. |

### One nuance that must survive

If the watchdog is ever mentioned, keep the framing intact: **it is a diagnosis tool, not a
rescue.** Java cannot safely kill a thread (`Thread.stop` throws `UnsupportedOperationException`
on JDK 20+), and killing mid-round would leave the engine half-mutated — a piece removed from
one cell and never added to another. A frozen game is bad; a silently corrupted one is worse.

---

## 11. Handoff — next steps in priority order

```
  NOW ──▶ [ tutor reviews the GUI ]        ← brief step 2 checkpoint, currently blocking
             │
             ▼
   1.  client.testing.TestClientMain       25%  ← two+ async clients, rapid succession
             │                                    ALSO produces §9's load inputs
             ▼
   2.  persistence/ + H2 as own process    20%  ← three applications; db/schema.sql, db/seed.sql
             │                                    seam is GameRepository.NO_OP in ServerMain
             ▼
   3.  benchmark harness (§9)              20%  ← fills the 🔴 slots
             │
             ▼
   4.  report assembly                          ← from IMPLEMENTATION-NOTES.md + this document
```

### Housekeeping, safe to do any time

- **`coverage.ps1`** — add `Remove-Item -Recurse -Force` for `out\test\` and `out\coverage-test\`
  before compiling. Removes the 7 phantom failures and makes SonarQube numbers honest.
- **`sonar-project.properties` contains a live token** (`sonar.token=sqa_f2f1…`) committed to
  git. Localhost, so the blast radius is small — but it is in the history. Rotate it and move it
  to an environment variable.

### For the report

Section (b) assembles directly from §3 (the measured worked example), §4 (mechanism selection),
§7 (the threading comparison) and §8 (accepted constraints). Section (a) assembles from
`IMPLEMENTATION-NOTES.md` §2 and §4.

Keep the evidence labels. A report that distinguishes *measured* from *reasoned* is more
credible than one that blurs them — and under questioning, it is the difference between a
defensible claim and an indefensible one.
