package server;

import adapter.GameController;
import adapter.GameEventBroadcaster;
import adapter.LobbyController;
import adapter.RequestRouter;
import app.SessionRegistry;
import app.port.Clock;
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
import net.ConnectionRegistry;
import net.LudoServer;
import net.RequestQueue;

import java.io.IOException;

/**
 * The composition root: the one place where every dependency is created and wired together.
 *
 * <p>Because construction lives here and nowhere else, no interactor and no engine class ever
 * builds its own collaborators or reaches for a static singleton — which is what makes each of
 * them substitutable in a test. The wiring order below reads outward through the layers, and
 * each inner component is handed its outer collaborator disguised as a port it declared
 * itself: {@code SessionRegistry} receives the {@link ConnectionRegistry} but sees only an
 * {@code EventSink}.
 *
 * <p>Shared by {@link ServerMain} and by the integration tests, so what the tests exercise is
 * the same assembly the tutor will see running, not a parallel one that could drift.
 */
public final class ServerAssembly implements AutoCloseable {

    private final RequestQueue requestQueue;
    private final ConnectionRegistry connections;
    private final SessionRegistry sessions;
    private final LudoServer server;

    public ServerAssembly(ServerConfig config, GameRepository repository, Clock clock) {
        this.requestQueue = new RequestQueue(config.workers(), config.queueCapacity());
        this.connections = new ConnectionRegistry(clock, requestQueue);

        this.sessions = new SessionRegistry(
                connections,   // seen only as an EventSink
                repository,
                clock,
                GameEventBroadcaster::new); // seen only as a SessionListenerFactory

        // Closes the wiring cycle: sessions publish to connections, and connections report
        // how many sessions are live and release viewer counts when a client disconnects.
        UnsubscribeFromGameUseCase unsubscribeFromGame = new UnsubscribeFromGameUseCase(sessions);
        connections.setLiveSessionCount(sessions::liveCount);
        connections.setSubscriptionReleaser(unsubscribeFromGame::execute);

        GameController gameController = new GameController(
                new StartGameUseCase(sessions),
                new PauseGameUseCase(sessions),
                new ResumeGameUseCase(sessions),
                new StepRoundUseCase(sessions),
                new AbortGameUseCase(sessions),
                new SetSpeedUseCase(sessions),
                new GetSnapshotUseCase(sessions));

        LobbyController lobbyController = new LobbyController(
                new CreateGameUseCase(sessions),
                new ListGamesUseCase(sessions),
                new SubscribeToGameUseCase(sessions),
                unsubscribeFromGame,
                connections);

        RequestRouter router = new RequestRouter(gameController, lobbyController);
        this.server = new LudoServer(config.port(), router, requestQueue, connections);
    }

    /** Binds the port and begins accepting clients. */
    public void start() throws IOException {
        server.start();
    }

    /** The bound port — resolves the real port when the config asked for 0. */
    public int getPort() {
        return server.getPort();
    }

    public ConnectionRegistry getConnections() {
        return connections;
    }

    public SessionRegistry getSessions() {
        return sessions;
    }

    public RequestQueue getRequestQueue() {
        return requestQueue;
    }

    /** Shuts every tier down in the reverse of the wiring order. */
    @Override
    public void close() {
        server.close();
        connections.closeAll();
        sessions.shutdown();
        requestQueue.close();
    }
}
