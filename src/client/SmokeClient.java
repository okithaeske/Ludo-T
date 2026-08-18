package client;

import shared.BoardSnapshot;
import shared.Command;
import shared.PlayerDto;
import shared.ProtocolCodec;
import shared.Request;
import shared.Response;
import shared.ServerEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A keyboard-driven client for exercising the server before any GUI exists.
 *
 * <p>Deliberately small, and deliberately built the same shape as the real client will be: a
 * dedicated receiver thread prints whatever arrives, while the main thread sends. Because the
 * two are independent, server pushes appear as they happen rather than only when a reply is
 * being waited for — which is the property the GUI depends on and the reason it is worth
 * proving here, with nothing else in the way.
 *
 * <p>Run two of these against one server and drive a game from one: the other prints the
 * board changing without asking for it.
 *
 * <pre>
 *   create LUDO_T 42 200   create a seeded Ludo-T game ticking every 200ms
 *   sub g1                 watch game g1
 *   start g1               let it run
 *   pause g1 / resume g1   control it — watch the other client react
 *   step g1                advance exactly one round
 *   snap g1                print the board right now
 *   list / metrics / ping
 *   quit
 * </pre>
 */
public final class SmokeClient {

    private static final AtomicLong REQUEST_IDS = new AtomicLong();

    private SmokeClient() {
        // Entry point only.
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5599;

        try {
            run(host, port);
        } catch (IOException e) {
            System.out.println("[error] " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        ProtocolCodec codec = ProtocolCodec.open(socket);

        Thread receiver = new Thread(() -> receiveLoop(codec), "receiver");
        receiver.setDaemon(true);
        receiver.start();

        System.out.println("Connected to " + host + ":" + port + ". Type 'help' for commands.");
        runCommandLoop(codec);

        codec.close();
    }

    /** Prints everything the server sends, whether asked for or not. */
    private static void receiveLoop(ProtocolCodec codec) {
        try {
            while (true) {
                Object frame = codec.read();
                if (frame instanceof Response response) {
                    printResponse(response);
                } else if (frame instanceof ServerEvent event) {
                    printEvent(event);
                } else {
                    System.out.println("? unexpected frame: " + frame);
                }
            }
        } catch (IOException e) {
            System.out.println("[disconnected] " + e.getMessage());
        }
    }

    private static void printResponse(Response response) {
        Object payload = response.getPayload();
        if (payload instanceof BoardSnapshot snapshot) {
            System.out.println("<- " + response.getStatus() + " #" + response.getRequestId());
            printBoard(snapshot);
        } else {
            System.out.println("<- " + response);
        }
    }

    private static void printEvent(ServerEvent event) {
        switch (event.getType()) {
            case LOG -> System.out.println("   [" + event.getGameId() + "] " + event.getPayload());
            case SNAPSHOT, GAME_OVER -> {
                BoardSnapshot snapshot = (BoardSnapshot) event.getPayload();
                System.out.println("== " + event.getType() + " " + event.getGameId()
                        + " round " + snapshot.round());
                if (event.getType() == shared.ServerEventType.GAME_OVER) {
                    printBoard(snapshot);
                }
            }
            default -> System.out.println("== " + event.getType() + " " + event.getPayload());
        }
    }

    private static void printBoard(BoardSnapshot snapshot) {
        System.out.println("   game " + snapshot.gameId() + " " + snapshot.mode()
                + " " + snapshot.state() + " round=" + snapshot.round()
                + (snapshot.hasMysteryCell() ? " mystery@" + snapshot.mysteryPosition() : ""));
        for (PlayerDto player : snapshot.players()) {
            System.out.println("   " + String.format("%-7s", player.colour().toLowerCase())
                    + " board=" + player.piecesOnBoard()
                    + " base=" + player.piecesAtBase()
                    + " home=" + player.piecesHome()
                    + " captures=" + player.totalCaptures());
        }
        if (!snapshot.finishingOrder().isEmpty()) {
            System.out.println("   standings: " + snapshot.finishingOrder());
        }
    }

    private static void runCommandLoop(ProtocolCodec codec) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            String verb = parts[0].toLowerCase();

            if (verb.isEmpty()) {
                continue;
            }
            if (verb.equals("quit") || verb.equals("exit")) {
                return;
            }
            if (verb.equals("help")) {
                printHelp();
                continue;
            }
            if (verb.equals("sleep")) {
                // Lets a demo be written as a script file and replayed identically, instead of
                // depending on how fast someone types.
                sleepQuietly(parts.length > 1 ? Long.parseLong(parts[1]) : 1000L);
                continue;
            }

            Request request = buildRequest(verb, parts);
            if (request == null) {
                System.out.println("Unknown command: " + verb + " (try 'help')");
                continue;
            }
            try {
                codec.write(request);
            } catch (IOException e) {
                // The server went away. Report it plainly and stop; a stack trace here tells
                // the user nothing they can act on.
                System.out.println("[disconnected] " + e.getMessage());
                return;
            }
        }
    }

    private static Request buildRequest(String verb, String[] parts) {
        long id = REQUEST_IDS.incrementAndGet();
        return switch (verb) {
            case "ping" -> Request.of(id, Command.PING);
            case "list" -> Request.of(id, Command.LIST_GAMES);
            case "metrics" -> Request.of(id, Command.GET_METRICS);
            case "create" -> new Request(id, Command.CREATE_GAME, createParams(parts));
            case "start" -> gameRequest(id, Command.START_GAME, parts);
            case "pause" -> gameRequest(id, Command.PAUSE_GAME, parts);
            case "resume" -> gameRequest(id, Command.RESUME_GAME, parts);
            case "step" -> gameRequest(id, Command.STEP_ROUND, parts);
            case "abort" -> gameRequest(id, Command.ABORT_GAME, parts);
            case "snap" -> gameRequest(id, Command.GET_SNAPSHOT, parts);
            case "sub" -> gameRequest(id, Command.SUBSCRIBE, parts);
            case "unsub" -> gameRequest(id, Command.UNSUBSCRIBE, parts);
            case "speed" -> gameRequest(id, Command.SET_SPEED, parts)
                    .with("tickMillis", parts.length > 2 ? parts[2] : "250");
            default -> null;
        };
    }

    private static Request gameRequest(long id, Command command, String[] parts) {
        return Request.of(id, command, "gameId", parts.length > 1 ? parts[1] : "");
    }

    private static Map<String, String> createParams(String[] parts) {
        Map<String, String> params = new HashMap<>();
        params.put("mode", parts.length > 1 ? parts[1] : "LUDO_T");
        if (parts.length > 2) {
            params.put("seed", parts[2]);
        }
        params.put("tickMillis", parts.length > 3 ? parts[3] : "250");
        return params;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printHelp() {
        List<String> lines = List.of(
                "  create [MODE] [seed] [tickMillis]   create a game (default LUDO_T, unseeded)",
                "  list                                list all sessions",
                "  sub <id> / unsub <id>               start/stop receiving pushes for a game",
                "  start|pause|resume|step|abort <id>  control a game",
                "  speed <id> <millis>                 change round speed",
                "  snap <id>                           print the board now",
                "  metrics                             server queue and connection load",
                "  ping                                liveness check",
                "  sleep <millis>                      pause, so demos can be scripted to a file",
                "  quit");
        lines.forEach(System.out::println);
    }
}
