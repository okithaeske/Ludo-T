package test;

import adapter.ClientSession;
import adapter.GameController;
import adapter.GameEventBroadcaster;
import adapter.LobbyController;
import adapter.RequestRouter;
import app.SessionRegistry;
import app.port.Clock;
import app.port.EventSink;
import app.port.GameRepository;
import app.usecase.AbortGameUseCase;
import app.usecase.CreateGameUseCase;
import app.usecase.GetSnapshotUseCase;
import app.usecase.ListGamesUseCase;
import app.usecase.PauseGameUseCase;
import app.usecase.ResumeGameUseCase;
import app.usecase.SetSpeedUseCase;
import app.usecase.StartGameUseCase;
import app.usecase.StepRoundUseCase;
import app.usecase.SubscribeToGameUseCase;
import app.usecase.UnsubscribeFromGameUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shared.BoardSnapshot;
import shared.Command;
import shared.Request;
import shared.Response;
import shared.ResponseStatus;
import shared.ServerMetricsDto;
import shared.SessionSummaryDto;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exercises the whole adapter and application stack with no socket anywhere — which is the
 * point of the ports: everything below {@code net} is testable in-process.
 */
@DisplayName("RequestRouter")
class RequestRouterTest {

    private static final ServerMetricsDto STUB_METRICS =
            new ServerMetricsDto(0, 16, 0, 2, 0, 0, 0, 1, 0);

    private final AtomicLong requestIds = new AtomicLong();
    private SessionRegistry registry;
    private RequestRouter router;
    private FakeClient client;

    /** In-memory stand-in for a network connection. */
    private static final class FakeClient implements ClientSession {

        private final Set<String> subscriptions = new HashSet<>();

        @Override public String id() { return "test-client"; }
        @Override public void subscribe(String gameId) { subscriptions.add(gameId); }
        @Override public void unsubscribe(String gameId) { subscriptions.remove(gameId); }
        @Override public boolean isSubscribed(String gameId) { return subscriptions.contains(gameId); }
    }

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry(EventSink.NO_OP, GameRepository.NO_OP, Clock.SYSTEM,
                GameEventBroadcaster::new);

        GameController games = new GameController(
                new StartGameUseCase(registry), new PauseGameUseCase(registry),
                new ResumeGameUseCase(registry), new StepRoundUseCase(registry),
                new AbortGameUseCase(registry), new SetSpeedUseCase(registry),
                new GetSnapshotUseCase(registry));

        LobbyController lobby = new LobbyController(
                new CreateGameUseCase(registry), new ListGamesUseCase(registry),
                new SubscribeToGameUseCase(registry), new UnsubscribeFromGameUseCase(registry),
                () -> STUB_METRICS);

        router = new RequestRouter(games, lobby);
        client = new FakeClient();
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    private Response send(Command command, Map<String, String> params) {
        return router.route(new Request(requestIds.incrementAndGet(), command, params), client);
    }

    private String createGame() {
        Response response = send(Command.CREATE_GAME,
                Map.of("mode", "LUDO_T", "seed", "42", "tickMillis", "0"));
        Assertions.assertTrue(response.isOk(), response.getMessage());
        return ((SessionSummaryDto) response.getPayload()).gameId();
    }

    @Test
    @DisplayName("should_answerPong_when_pinged")
    void should_answerPong_when_pinged() {
        Response response = send(Command.PING, Map.of());

        Assertions.assertTrue(response.isOk());
        Assertions.assertEquals("pong", response.getMessage());
    }

    @Test
    @DisplayName("should_createSeededGame_when_parametersAreValid")
    void should_createSeededGame_when_parametersAreValid() {
        Response response = send(Command.CREATE_GAME,
                Map.of("mode", "LUDO_T", "seed", "42", "tickMillis", "0"));

        SessionSummaryDto summary = (SessionSummaryDto) response.getPayload();
        Assertions.assertEquals("LUDO_T", summary.mode());
        Assertions.assertEquals(42L, summary.seed());
        Assertions.assertEquals("CREATED", summary.state());
        Assertions.assertTrue(summary.isSeeded());
    }

    @Test
    @DisplayName("should_returnError_when_modeIsNotRecognised")
    void should_returnError_when_modeIsNotRecognised() {
        Response response = send(Command.CREATE_GAME, Map.of("mode", "CHESS"));

        // A bad parameter must produce a clean error, never an exception escaping the worker.
        Assertions.assertEquals(ResponseStatus.ERROR, response.getStatus());
        Assertions.assertTrue(response.getMessage().contains("CLASSIC"));
    }

    @Test
    @DisplayName("should_returnError_when_gameIdIsUnknown")
    void should_returnError_when_gameIdIsUnknown() {
        Response response = send(Command.START_GAME, Map.of("gameId", "nope"));

        Assertions.assertEquals(ResponseStatus.ERROR, response.getStatus());
        Assertions.assertTrue(response.getMessage().contains("nope"));
    }

    @Test
    @DisplayName("should_returnError_when_gameIdIsMissing")
    void should_returnError_when_gameIdIsMissing() {
        Response response = send(Command.PAUSE_GAME, Map.of());

        Assertions.assertEquals(ResponseStatus.ERROR, response.getStatus());
        Assertions.assertTrue(response.getMessage().contains("gameId"));
    }

    @Test
    @DisplayName("should_advanceBoard_when_stepIsRouted")
    void should_advanceBoard_when_stepIsRouted() {
        String gameId = createGame();

        send(Command.STEP_ROUND, Map.of("gameId", gameId));
        Response snapshot = send(Command.GET_SNAPSHOT, Map.of("gameId", gameId));

        BoardSnapshot board = (BoardSnapshot) snapshot.getPayload();
        Assertions.assertEquals(1, board.round());
        Assertions.assertEquals(4, board.players().size());
    }

    @Test
    @DisplayName("should_recordSubscription_when_subscribeIsRouted")
    void should_recordSubscription_when_subscribeIsRouted() {
        String gameId = createGame();

        Response subscribed = send(Command.SUBSCRIBE, Map.of("gameId", gameId));
        Assertions.assertTrue(client.isSubscribed(gameId));
        // The connection's routing set and the session's viewer count must move together,
        // or the lobby reports a viewer count that means nothing.
        Assertions.assertEquals(1, ((SessionSummaryDto) subscribed.getPayload()).subscribers());

        Response unsubscribed = send(Command.UNSUBSCRIBE, Map.of("gameId", gameId));
        Assertions.assertFalse(client.isSubscribed(gameId));
        Assertions.assertEquals(0, ((SessionSummaryDto) unsubscribed.getPayload()).subscribers());
    }

    @Test
    @DisplayName("should_notSubscribe_when_gameDoesNotExist")
    void should_notSubscribe_when_gameDoesNotExist() {
        Response response = send(Command.SUBSCRIBE, Map.of("gameId", "ghost"));

        Assertions.assertEquals(ResponseStatus.ERROR, response.getStatus());
        Assertions.assertFalse(client.isSubscribed("ghost"),
                "A typo must not leave the client subscribed to a game that cannot exist");
    }

    @Test
    @DisplayName("should_listCreatedGames_when_listIsRouted")
    void should_listCreatedGames_when_listIsRouted() {
        createGame();
        createGame();

        Response response = send(Command.LIST_GAMES, Map.of());

        @SuppressWarnings("unchecked")
        java.util.List<SessionSummaryDto> games =
                (java.util.List<SessionSummaryDto>) response.getPayload();
        Assertions.assertEquals(2, games.size());
    }

    @Test
    @DisplayName("should_returnMetrics_when_metricsAreRequested")
    void should_returnMetrics_when_metricsAreRequested() {
        Response response = send(Command.GET_METRICS, Map.of());

        Assertions.assertTrue(response.isOk());
        Assertions.assertInstanceOf(ServerMetricsDto.class, response.getPayload());
    }

    @Test
    @DisplayName("should_returnError_when_tickMillisIsNotANumber")
    void should_returnError_when_tickMillisIsNotANumber() {
        String gameId = createGame();

        Response response = send(Command.SET_SPEED, Map.of("gameId", gameId, "tickMillis", "fast"));

        Assertions.assertEquals(ResponseStatus.ERROR, response.getStatus());
    }
}
