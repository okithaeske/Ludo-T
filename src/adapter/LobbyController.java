package adapter;

import app.model.SessionSummary;
import app.usecase.CreateGameUseCase;
import app.usecase.ListGamesUseCase;
import app.usecase.SessionNotFoundException;
import app.usecase.SubscribeToGameUseCase;
import app.usecase.UnsubscribeFromGameUseCase;
import enums.GameMode;
import shared.Request;
import shared.Response;

import java.io.Serializable;
import java.util.ArrayList;

/** Handles the commands that concern the server as a whole rather than one game. */
public final class LobbyController {

    private static final String PARAM_MODE = "mode";
    private static final String PARAM_SEED = "seed";
    private static final String PARAM_TICK_MILLIS = "tickMillis";
    private static final String PARAM_GAME_ID = "gameId";

    private static final long DEFAULT_TICK_MILLIS = 250L;

    private final CreateGameUseCase createGame;
    private final ListGamesUseCase listGames;
    private final SubscribeToGameUseCase subscribeToGame;
    private final UnsubscribeFromGameUseCase unsubscribeFromGame;
    private final MetricsProvider metrics;

    public LobbyController(CreateGameUseCase createGame, ListGamesUseCase listGames,
                           SubscribeToGameUseCase subscribeToGame,
                           UnsubscribeFromGameUseCase unsubscribeFromGame,
                           MetricsProvider metrics) {
        this.createGame = createGame;
        this.listGames = listGames;
        this.subscribeToGame = subscribeToGame;
        this.unsubscribeFromGame = unsubscribeFromGame;
        this.metrics = metrics;
    }

    public Response ping(Request request) {
        return Response.ok(request.getId(), "pong");
    }

    public Response create(Request request) {
        GameMode mode;
        try {
            mode = GameMode.valueOf(request.paramOrDefault(PARAM_MODE, "LUDO_T")
                    .trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.error(request.getId(),
                    "mode must be CLASSIC or LUDO_T, got: " + request.param(PARAM_MODE));
        }

        Long seed;
        try {
            seed = parseSeed(request.param(PARAM_SEED));
        } catch (NumberFormatException e) {
            return Response.error(request.getId(),
                    "seed must be a whole number, got: " + request.param(PARAM_SEED));
        }

        long tickMillis;
        try {
            tickMillis = Long.parseLong(
                    request.paramOrDefault(PARAM_TICK_MILLIS, String.valueOf(DEFAULT_TICK_MILLIS)).trim());
        } catch (NumberFormatException e) {
            return Response.error(request.getId(),
                    "tickMillis must be a whole number, got: " + request.param(PARAM_TICK_MILLIS));
        }

        SessionSummary summary = createGame.execute(mode, seed, tickMillis);
        return Response.ok(request.getId(), SnapshotMapper.toDto(summary));
    }

    public Response list(Request request) {
        // ArrayList rather than the mapper's List directly: the payload must be serialisable,
        // and List.copyOf produces an immutable type that is not part of the wire contract.
        Serializable payload = new ArrayList<>(SnapshotMapper.toDtos(listGames.execute()));
        return Response.ok(request.getId(), payload);
    }

    public Response subscribe(Request request, ClientSession client) {
        String gameId = request.param(PARAM_GAME_ID);
        if (gameId == null || gameId.isBlank()) {
            return Response.error(request.getId(), "gameId is required");
        }
        // Reject unknown games before touching the connection, so a typo cannot leave the
        // client subscribed to a game that will never exist.
        SessionSummary summary;
        try {
            summary = subscribeToGame.execute(gameId);
        } catch (SessionNotFoundException e) {
            return Response.error(request.getId(), e.getMessage());
        }
        client.subscribe(gameId);
        return Response.ok(request.getId(), SnapshotMapper.toDto(summary));
    }

    public Response unsubscribe(Request request, ClientSession client) {
        String gameId = request.param(PARAM_GAME_ID);
        if (gameId == null || gameId.isBlank()) {
            return Response.error(request.getId(), "gameId is required");
        }
        client.unsubscribe(gameId);
        try {
            return Response.ok(request.getId(),
                    SnapshotMapper.toDto(unsubscribeFromGame.execute(gameId)));
        } catch (SessionNotFoundException e) {
            // The game is already gone; the client is unsubscribed either way.
            return Response.ok(request.getId(), "unsubscribed from " + gameId);
        }
    }

    public Response metrics(Request request) {
        return Response.ok(request.getId(), metrics.currentMetrics());
    }

    private static Long parseSeed(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Long.valueOf(raw.trim());
    }
}
