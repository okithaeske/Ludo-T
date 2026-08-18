package adapter;

import shared.Command;
import shared.Request;
import shared.Response;

import java.util.EnumMap;
import java.util.Map;

/**
 * Dispatches a {@link Request} to the controller that handles its {@link Command}.
 *
 * <p>A table lookup rather than a switch: adding a command means registering a handler, and an
 * unregistered command produces a clean error response instead of falling through a default
 * branch. The table is built once at construction and never mutated, so it is safe to share
 * across every worker thread without synchronisation.
 *
 * <p>Routing itself never throws. A controller that fails turns into an ERROR response, so one
 * malformed request can never take down the worker that happened to pick it up.
 */
public final class RequestRouter {

    /** How a single command is handled. */
    @FunctionalInterface
    private interface Handler {
        Response handle(Request request, ClientSession client);
    }

    private final Map<Command, Handler> handlers = new EnumMap<>(Command.class);

    public RequestRouter(GameController games, LobbyController lobby) {
        handlers.put(Command.PING, (request, client) -> lobby.ping(request));
        handlers.put(Command.CREATE_GAME, (request, client) -> lobby.create(request));
        handlers.put(Command.LIST_GAMES, (request, client) -> lobby.list(request));
        handlers.put(Command.GET_METRICS, (request, client) -> lobby.metrics(request));
        handlers.put(Command.SUBSCRIBE, lobby::subscribe);
        handlers.put(Command.UNSUBSCRIBE, lobby::unsubscribe);

        handlers.put(Command.START_GAME, (request, client) -> games.start(request));
        handlers.put(Command.PAUSE_GAME, (request, client) -> games.pause(request));
        handlers.put(Command.RESUME_GAME, (request, client) -> games.resume(request));
        handlers.put(Command.STEP_ROUND, (request, client) -> games.step(request));
        handlers.put(Command.ABORT_GAME, (request, client) -> games.abort(request));
        handlers.put(Command.SET_SPEED, (request, client) -> games.setSpeed(request));
        handlers.put(Command.GET_SNAPSHOT, (request, client) -> games.snapshot(request));
    }

    /** Routes one request. Always returns a response; never throws. */
    public Response route(Request request, ClientSession client) {
        if (request == null) {
            return Response.error(-1L, "Empty request");
        }
        Handler handler = handlers.get(request.getCommand());
        if (handler == null) {
            return Response.error(request.getId(),
                    "Unsupported command: " + request.getCommand());
        }
        try {
            return handler.handle(request, client);
        } catch (RuntimeException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Response.error(request.getId(), detail);
        }
    }
}
