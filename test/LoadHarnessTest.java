package test;

import app.port.Clock;
import app.port.GameRepository;
import client.testing.AsyncConnection;
import client.testing.LatencyRecorder;
import client.testing.LoadClient;
import client.testing.LoadConfig;
import client.testing.Workload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.ServerAssembly;
import server.ServerConfig;
import shared.Command;
import shared.Response;
import shared.ResponseStatus;
import shared.SessionSummaryDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Guards the load harness itself.
 *
 * <p>A load test is evidence only if the harness is trustworthy, and a harness has two ways to
 * lie that are invisible in its own output: it can lose requests and call the run clean, or it
 * can compute percentiles wrongly and make a slow server look fast. Both are checked here
 * against a real server on a real socket, so a broken harness fails the suite rather than
 * quietly producing numbers for the report.
 *
 * <p>The server is deliberately built small — two workers, a sixteen-deep queue — so the
 * saturation path is exercised inside the test rather than only during a demo.
 */
@DisplayName("Load harness (headless clients over real sockets)")
class LoadHarnessTest {

    private ServerAssembly server;
    private AsyncConnection control;
    private final List<LoadClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        // Port 0: the OS picks a free port, so the suite never clashes with a running server.
        server = new ServerAssembly(new ServerConfig(0, 2, 16, 1_000L),
                GameRepository.NO_OP, Clock.SYSTEM);
        server.start();
        control = AsyncConnection.connect("test-control", "localhost", server.getPort());
    }

    @AfterEach
    void stopServer() {
        clients.forEach(LoadClient::close);
        clients.clear();
        if (control != null) {
            control.close();
        }
        server.close();
    }

    @Test
    @DisplayName("should_reportOrderStatistics_when_latenciesAreRecorded")
    void should_reportOrderStatistics_when_latenciesAreRecorded() {
        // Arrange — 1..100 nanoseconds, so every percentile has an obvious right answer
        LatencyRecorder recorder = new LatencyRecorder("TEST");
        for (int nanos = 1; nanos <= 100; nanos++) {
            recorder.record(ResponseStatus.OK, nanos);
        }
        recorder.record(ResponseStatus.REJECTED, 500L);
        recorder.recordFailure();

        // Act
        LatencyRecorder.Stats stats = recorder.stats();

        // Assert — percentiles, and the counts a report must not blur together
        // 101 samples, nearest-rank: p50 is the 51st value, not the 50th. Pinned deliberately
        // — an off-by-one here would shift every percentile in every report by one sample.
        Assertions.assertEquals(51L, stats.p50Nanos(), "p50 of 1..100 plus one outlier");
        Assertions.assertEquals(100L, stats.p99Nanos(), "p99 must not yet reach the outlier");
        Assertions.assertEquals(500L, stats.maxNanos(), "the outlier must survive as the max");
        Assertions.assertEquals(100L, stats.ok());
        Assertions.assertEquals(1L, stats.rejected());
        Assertions.assertEquals(1L, stats.failures(), "an unanswered request is not a latency");
        Assertions.assertEquals(102L, stats.total());
    }

    @Test
    @DisplayName("should_answerEveryRequest_when_twoClientsFloodAsynchronously")
    void should_answerEveryRequest_when_twoClientsFloodAsynchronously() throws Exception {
        // Arrange — two independent connections, four threads each, aimed at one live game
        List<String> games = List.of(createGame());
        LoadConfig config = loadConfig(2, 4, 60, Workload.MIXED, games.size());
        Map<Command, LatencyRecorder> recorders = LoadClient.newRecorders();

        // Act
        runLoad(config, games, recorders);

        // Assert — the whole point of a bounded queue with caller-runs: throttled, never lost
        long sent = clients.stream().mapToLong(LoadClient::sentCount).sum();
        long answered = clients.stream().mapToLong(LoadClient::answeredCount).sum();
        Assertions.assertEquals(config.clients() * config.threads() * config.requestsPerThread(),
                sent, "every worker should have spent its whole budget");
        Assertions.assertEquals(sent, answered, "the server dropped requests under saturation");

        long timed = recorders.values().stream()
                .mapToLong(recorder -> recorder.stats().ok() + recorder.stats().rejected()).sum();
        Assertions.assertEquals(answered, timed, "an answer was counted but never timed");
    }

    @Test
    @DisplayName("should_reportNoErrors_when_lifecycleCommandsRaceOnOneGame")
    void should_reportNoErrors_when_lifecycleCommandsRaceOnOneGame() throws Exception {
        // Arrange — the control mix is almost entirely mutating commands, all aimed at the same
        // game, so eight threads contend for one actor's mailbox for the whole run.
        List<String> games = List.of(createGame());
        LoadConfig config = loadConfig(2, 4, 40, Workload.CONTROL, games.size());
        Map<Command, LatencyRecorder> recorders = LoadClient.newRecorders();

        // Act
        runLoad(config, games, recorders);

        // Assert — arrival order decides who was first and both callers get a definite answer.
        // An error here would mean the session had been raced into a state it could not
        // describe, which is exactly what confining a game to one thread is supposed to prevent.
        long errors = recorders.values().stream()
                .mapToLong(recorder -> recorder.stats().errors()).sum();
        long failures = recorders.values().stream()
                .mapToLong(recorder -> recorder.stats().failures()).sum();
        Assertions.assertEquals(0L, errors, "contending lifecycle commands produced an error");
        Assertions.assertEquals(0L, failures, "a request was never answered");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createGame() throws Exception {
        Response created = control.call(Command.CREATE_GAME,
                Map.of("mode", "LUDO_T", "seed", "42", "tickMillis", "20"), 15_000L);
        Assertions.assertTrue(created.isOk(), "could not create a game: " + created.getMessage());
        String gameId = ((SessionSummaryDto) created.getPayload()).gameId();
        control.call(Command.START_GAME, Map.of("gameId", gameId), 15_000L);
        return gameId;
    }

    private LoadConfig loadConfig(int clientCount, int threads, int requests,
                                  Workload mix, int games) {
        LoadConfig base = LoadConfig.defaults();
        return new LoadConfig("localhost", server.getPort(), clientCount, threads, requests,
                0L, games, "LUDO_T", 20L, mix,
                16,        // a tight in-flight cap keeps the test quick and still saturates 2 workers
                7L, false, // no subscriptions: this test is about answers, not pushes
                true, false, base.sampleMillis(), null);
    }

    private void runLoad(LoadConfig config, List<String> games,
                         Map<Command, LatencyRecorder> recorders) throws Exception {
        for (int i = 0; i < config.clients(); i++) {
            clients.add(new LoadClient(i, config, games, recorders));
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        clients.forEach(client -> client.start(deadlineNanos));
        for (LoadClient client : clients) {
            client.awaitCompletion();
        }
    }
}
