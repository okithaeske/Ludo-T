package client.testing;

import shared.Command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The command mixes a load run can fire, and what each one is for.
 *
 * <h2>Why a mix rather than one command repeated</h2>
 * Hammering {@code PING} would measure the socket and the request queue and nothing else —
 * every request would complete without ever reaching a game actor, so the numbers would say
 * nothing about the design being defended. The mixes here deliberately differ in <em>which
 * tier absorbs the work</em>:
 *
 * <ul>
 *   <li>{@link #READ} — answered by the request-worker pool and, for snapshots, by a game
 *       actor. Every command is always valid, so the latency figures are clean and
 *       comparable between runs. This is the mix to quote for percentiles.</li>
 *   <li>{@link #MIXED} — reads plus lifecycle commands aimed at the same few games, so
 *       several clients contend for one actor's mailbox. This is the mix that exercises the
 *       claim that operations on one game are serialised in arrival order while different
 *       games proceed in parallel.</li>
 *   <li>{@link #CONTROL} — almost entirely mutating commands. Each one queues behind a
 *       running round, so this is the mix that fills the inbound queue fastest and makes
 *       caller-runs backpressure visible in the metrics sample.</li>
 * </ul>
 *
 * <h2>Why nothing here can fail legitimately</h2>
 * Every lifecycle command on {@code GameSession} is a no-op when it does not apply — a pause
 * on an already paused game returns its summary rather than an error. That is deliberate on
 * the server's part and convenient here: any {@code ERROR} in a report is a genuine defect,
 * not the harness racing itself into an invalid state. {@code ABORT_GAME} is excluded for the
 * same reason — a workload that destroyed its own targets would report shrinking latencies as
 * the server ran out of work to do.
 */
public enum Workload {

    READ(new Command[] {
            Command.GET_SNAPSHOT, Command.GET_SNAPSHOT, Command.GET_SNAPSHOT,
            Command.GET_SNAPSHOT, Command.GET_SNAPSHOT,
            Command.LIST_GAMES, Command.LIST_GAMES,
            Command.GET_METRICS,
            Command.PING, Command.PING }),

    MIXED(new Command[] {
            Command.GET_SNAPSHOT, Command.GET_SNAPSHOT, Command.GET_SNAPSHOT,
            Command.GET_SNAPSHOT, Command.GET_SNAPSHOT,
            Command.LIST_GAMES, Command.LIST_GAMES,
            Command.GET_METRICS,
            Command.PING,
            Command.SET_SPEED,
            Command.PAUSE_GAME,
            Command.RESUME_GAME,
            Command.STEP_ROUND }),

    CONTROL(new Command[] {
            Command.STEP_ROUND, Command.STEP_ROUND, Command.STEP_ROUND, Command.STEP_ROUND,
            Command.PAUSE_GAME, Command.PAUSE_GAME,
            Command.RESUME_GAME, Command.RESUME_GAME,
            Command.SET_SPEED, Command.SET_SPEED,
            Command.GET_SNAPSHOT, Command.GET_SNAPSHOT });

    /** Speeds a SET_SPEED request may ask for — all sane, so a run cannot stall its own games. */
    private static final String[] TICKS = { "150", "200", "250", "300" };

    private final Command[] weighted;

    Workload(Command[] weighted) {
        this.weighted = weighted;
    }

    /** Parses the {@code --mix=} flag; unknown values fall back to {@link #MIXED}. */
    public static Workload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MIXED;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MIXED;
        }
    }

    /**
     * Picks the next command. The array is the weighting: repeating an entry raises its share,
     * which keeps the definition readable next to a map of probabilities that has to be kept
     * summing to one.
     */
    public Command next(Random random) {
        return weighted[random.nextInt(weighted.length)];
    }

    /** Builds the parameters for one command, choosing a game at random when it needs one. */
    public static Map<String, String> paramsFor(Command command, Random random,
                                                List<String> gameIds) {
        return switch (command) {
            case PING, LIST_GAMES, GET_METRICS -> Map.of();
            case SET_SPEED -> Map.of(
                    "gameId", pickGame(random, gameIds),
                    "tickMillis", TICKS[random.nextInt(TICKS.length)]);
            default -> Map.of("gameId", pickGame(random, gameIds));
        };
    }

    private static String pickGame(Random random, List<String> gameIds) {
        // No games means the harness is running against an empty server; the server answers
        // with a plain error, which is the honest thing for the report to show.
        return gameIds.isEmpty() ? "none" : gameIds.get(random.nextInt(gameIds.size()));
    }
}
