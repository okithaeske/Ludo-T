-- ─────────────────────────────────────────────────────────────────────────────
-- seed.sql  —  populate the database with historical games
--
-- Applied by:  run-db.ps1 -Init   (schema.sql runs first)
--
-- The brief asks for SQL that creates AND populates the database, and for the
-- database to be in its initial state at demonstration time. These six finished
-- games are that initial state: they give the GUI's History and Leaderboard tabs
-- something to show the moment the tutor sits down, before a single live game has
-- been run. Every game the server finishes during the demonstration is then
-- appended to this by JdbcGameRepository, so the tabs visibly grow.
--
-- The colour/name/strategy triples match what the engine actually produces
-- (player/RedPlayer.java and its siblings), so a seeded row is indistinguishable
-- in shape from a recorded one.
-- ─────────────────────────────────────────────────────────────────────────────

-- Sessions that began.
INSERT INTO game_session (game_id, mode, seed, tick_millis, created_at) VALUES
    ('seed-0001', 'CLASSIC', 20250101, 250, TIMESTAMP '2026-09-01 09:14:02'),
    ('seed-0002', 'LUDO_T',  20250102, 250, TIMESTAMP '2026-09-01 09:31:48'),
    ('seed-0003', 'LUDO_T',  20250103, 150, TIMESTAMP '2026-09-02 14:05:11'),
    ('seed-0004', 'CLASSIC', 20250104, 250, TIMESTAMP '2026-09-03 11:22:37'),
    ('seed-0005', 'LUDO_T',  20250105, 200, TIMESTAMP '2026-09-04 16:48:55'),
    ('seed-0006', 'LUDO_T',  20250106, 250, TIMESTAMP '2026-09-05 10:02:19');

-- Finished games, newest last.
INSERT INTO game_result
    (game_id, mode, rounds, winner_colour, winner_strategy, finishing_order, finished_at) VALUES
    ('seed-0001', 'CLASSIC', 63, 'YELLOW', 'RacerStrategy',
     'YELLOW,RED,GREEN,BLUE',   TIMESTAMP '2026-09-01 09:18:44'),
    ('seed-0002', 'LUDO_T',  71, 'RED',    'AggressiveStrategy',
     'RED,BLUE,YELLOW,GREEN',   TIMESTAMP '2026-09-01 09:37:12'),
    ('seed-0003', 'LUDO_T',  58, 'BLUE',   'MysteryHunterStrategy',
     'BLUE,YELLOW,RED,GREEN',   TIMESTAMP '2026-09-02 14:08:03'),
    ('seed-0004', 'CLASSIC', 84, 'YELLOW', 'RacerStrategy',
     'YELLOW,GREEN,RED,BLUE',   TIMESTAMP '2026-09-03 11:29:50'),
    ('seed-0005', 'LUDO_T',  66, 'RED',    'AggressiveStrategy',
     'RED,YELLOW,BLUE,GREEN',   TIMESTAMP '2026-09-04 16:53:30'),
    ('seed-0006', 'LUDO_T',  49, 'YELLOW', 'RacerStrategy',
     'YELLOW,BLUE,RED,GREEN',   TIMESTAMP '2026-09-05 10:04:41');

-- Per-player outcomes. place 1..4 follows finishing_order above; every seeded
-- game ran to completion, so nothing here is left unfinished.
INSERT INTO player_result
    (game_id, colour, player_name, strategy, pieces_home, captures, finished, place) VALUES
    -- seed-0001
    ('seed-0001', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 1, TRUE, 1),
    ('seed-0001', 'RED',    'Red',    'AggressiveStrategy',    4, 6, TRUE, 2),
    ('seed-0001', 'GREEN',  'Green',  'BlockerStrategy',       4, 2, TRUE, 3),
    ('seed-0001', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 3, TRUE, 4),
    -- seed-0002
    ('seed-0002', 'RED',    'Red',    'AggressiveStrategy',    4, 8, TRUE, 1),
    ('seed-0002', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 4, TRUE, 2),
    ('seed-0002', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 0, TRUE, 3),
    ('seed-0002', 'GREEN',  'Green',  'BlockerStrategy',       4, 3, TRUE, 4),
    -- seed-0003
    ('seed-0003', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 5, TRUE, 1),
    ('seed-0003', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 1, TRUE, 2),
    ('seed-0003', 'RED',    'Red',    'AggressiveStrategy',    4, 7, TRUE, 3),
    ('seed-0003', 'GREEN',  'Green',  'BlockerStrategy',       4, 2, TRUE, 4),
    -- seed-0004
    ('seed-0004', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 2, TRUE, 1),
    ('seed-0004', 'GREEN',  'Green',  'BlockerStrategy',       4, 4, TRUE, 2),
    ('seed-0004', 'RED',    'Red',    'AggressiveStrategy',    4, 9, TRUE, 3),
    ('seed-0004', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 3, TRUE, 4),
    -- seed-0005
    ('seed-0005', 'RED',    'Red',    'AggressiveStrategy',    4, 7, TRUE, 1),
    ('seed-0005', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 1, TRUE, 2),
    ('seed-0005', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 4, TRUE, 3),
    ('seed-0005', 'GREEN',  'Green',  'BlockerStrategy',       4, 2, TRUE, 4),
    -- seed-0006
    ('seed-0006', 'YELLOW', 'Yellow', 'RacerStrategy',         4, 0, TRUE, 1),
    ('seed-0006', 'BLUE',   'Blue',   'MysteryHunterStrategy', 4, 3, TRUE, 2),
    ('seed-0006', 'RED',    'Red',    'AggressiveStrategy',    4, 5, TRUE, 3),
    ('seed-0006', 'GREEN',  'Green',  'BlockerStrategy',       4, 1, TRUE, 4);

-- A short event sample, so the history detail view is not empty for seed-0006.
INSERT INTO game_event (game_id, round, message, recorded_at) VALUES
    ('seed-0006', 1,  'Round 1: Red rolled 6 and left base.',        TIMESTAMP '2026-09-05 10:02:20'),
    ('seed-0006', 7,  'Round 7: Yellow captured Green at cell 22.',  TIMESTAMP '2026-09-05 10:02:34'),
    ('seed-0006', 23, 'Round 23: Blue reached the mystery cell.',    TIMESTAMP '2026-09-05 10:03:11'),
    ('seed-0006', 41, 'Round 41: Yellow moved its third piece home.', TIMESTAMP '2026-09-05 10:04:02'),
    ('seed-0006', 49, 'Round 49: Yellow finished first.',            TIMESTAMP '2026-09-05 10:04:41');
