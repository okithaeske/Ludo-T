-- ─────────────────────────────────────────────────────────────────────────────
-- schema.sql  —  the Ludo-T database tier
--
-- Applied by:  run-db.ps1 -Init      (or -Reset to return to the initial state)
--
-- The assignment brief requires the SQL that creates and populates the database
-- to be runnable before the demonstration, and the database to be in its initial
-- state when the tutor arrives. That is exactly what -Reset does: this file drops
-- every object before recreating it, so applying it twice is safe.
--
-- What this tier stores is the *metagame*, never the simulation. A running game
-- lives entirely in its GameSession actor's memory; the database records what
-- happened after the fact, so database latency can never become round latency.
-- See app/port/GameRepository.java for the contract that promises this.
-- ─────────────────────────────────────────────────────────────────────────────

-- Dropped in reverse dependency order so a re-run is idempotent.
DROP VIEW  IF EXISTS strategy_leaderboard;
DROP TABLE IF EXISTS player_result;
DROP TABLE IF EXISTS game_result;
DROP TABLE IF EXISTS game_event;
DROP TABLE IF EXISTS game_session;

-- ── One row per game that actually began ─────────────────────────────────────
-- Written by GameRepository.recordSessionCreated, from the session actor thread
-- at the moment the engine begins. A game that is created but never started has
-- no row here on purpose: nothing happened worth recording.
CREATE TABLE game_session (
    game_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    mode        VARCHAR(16)  NOT NULL,
    seed        BIGINT       NULL,          -- null when the game was left unseeded
    tick_millis BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── The event stream, one row per completed round ────────────────────────────
-- Deliberately has NO foreign key to game_session. The writer drops rows rather
-- than block a game when the database falls behind (see JdbcGameRepository), so
-- a session row can legitimately be missing while its events are present. A
-- constraint here would turn a dropped row into a cascade of failed inserts.
CREATE TABLE game_event (
    event_id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_id     VARCHAR(64)  NOT NULL,
    round       INT          NOT NULL,
    message     VARCHAR(512) NOT NULL,
    recorded_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_game_event_game ON game_event (game_id, round);

-- ── One row per finished game ────────────────────────────────────────────────
CREATE TABLE game_result (
    game_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    mode            VARCHAR(16)  NOT NULL,
    rounds          INT          NOT NULL,
    winner_colour   VARCHAR(16)  NULL,      -- null when the game ended with no finisher
    winner_strategy VARCHAR(64)  NULL,
    finishing_order VARCHAR(128) NOT NULL,  -- comma-separated colours, best first
    finished_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_game_result_finished ON game_result (finished_at DESC);

-- ── Per-player outcome of a finished game ────────────────────────────────────
-- This one DOES carry a foreign key: player rows are written inside the same
-- transaction as their game_result row, so the parent is guaranteed present.
CREATE TABLE player_result (
    game_id     VARCHAR(64) NOT NULL,
    colour      VARCHAR(16) NOT NULL,
    player_name VARCHAR(64) NOT NULL,
    strategy    VARCHAR(64) NOT NULL,
    pieces_home INT         NOT NULL,
    captures    INT         NOT NULL,
    finished    BOOLEAN     NOT NULL,
    place       INT         NULL,           -- 1 = winner; null when it never finished
    PRIMARY KEY (game_id, colour),
    CONSTRAINT fk_player_result_game
        FOREIGN KEY (game_id) REFERENCES game_result (game_id) ON DELETE CASCADE
);

CREATE INDEX idx_player_result_strategy ON player_result (strategy);

-- ── Which AI strategy actually wins ──────────────────────────────────────────
-- A view rather than a query built in Java: the aggregation is the database
-- tier's job, and keeping it here means the server ships rows, not arithmetic.
-- This is what the GUI's Leaderboard tab reads.
CREATE VIEW strategy_leaderboard AS
SELECT strategy,
       COUNT(*)                                          AS games_played,
       SUM(CASE WHEN place = 1 THEN 1 ELSE 0 END)        AS wins,
       SUM(captures)                                     AS total_captures,
       AVG(CAST(pieces_home AS DOUBLE))                  AS avg_pieces_home
FROM player_result
GROUP BY strategy;
