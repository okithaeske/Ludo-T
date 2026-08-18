package adapter;

import app.model.GameSnapshot;
import app.model.SessionSummary;
import app.usecase.AbortGameUseCase;
import app.usecase.GetSnapshotUseCase;
import app.usecase.PauseGameUseCase;
import app.usecase.ResumeGameUseCase;
import app.usecase.SessionNotFoundException;
import app.usecase.SetSpeedUseCase;
import app.usecase.StartGameUseCase;
import app.usecase.StepRoundUseCase;
import shared.Request;
import shared.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles the commands that act on one game.
 *
 * <p>Each use case returns a {@link CompletableFuture} completed on that game's actor thread.
 * This controller waits for it with a bounded timeout, on a worker thread from the request
 * pool — never on a session thread, and never on the thread reading the socket. Waiting here
 * is deliberate: a request pool of fixed size that blocks briefly is what produces
 * backpressure when clients flood the server, instead of accepting unbounded work and running
 * out of memory. The timeout means a wedged game costs one worker for a second rather than
 * leaking workers until the pool is dead.
 */
public final class GameController {

    /** Longest a worker will wait for a session to answer before giving up on it. */
    private static final long SESSION_TIMEOUT_MILLIS = 5_000L;

    private static final String PARAM_GAME_ID = "gameId";
    private static final String PARAM_TICK_MILLIS = "tickMillis";

    private final StartGameUseCase startGame;
    private final PauseGameUseCase pauseGame;
    private final ResumeGameUseCase resumeGame;
    private final StepRoundUseCase stepRound;
    private final AbortGameUseCase abortGame;
    private final SetSpeedUseCase setSpeed;
    private final GetSnapshotUseCase getSnapshot;

    public GameController(StartGameUseCase startGame, PauseGameUseCase pauseGame,
                          ResumeGameUseCase resumeGame, StepRoundUseCase stepRound,
                          AbortGameUseCase abortGame, SetSpeedUseCase setSpeed,
                          GetSnapshotUseCase getSnapshot) {
        this.startGame = startGame;
        this.pauseGame = pauseGame;
        this.resumeGame = resumeGame;
        this.stepRound = stepRound;
        this.abortGame = abortGame;
        this.setSpeed = setSpeed;
        this.getSnapshot = getSnapshot;
    }

    public Response start(Request request) {
        return awaitSummary(request, id -> startGame.execute(id));
    }

    public Response pause(Request request) {
        return awaitSummary(request, id -> pauseGame.execute(id));
    }

    public Response resume(Request request) {
        return awaitSummary(request, id -> resumeGame.execute(id));
    }

    public Response step(Request request) {
        return awaitSummary(request, id -> stepRound.execute(id));
    }

    public Response abort(Request request) {
        return awaitSummary(request, id -> abortGame.execute(id));
    }

    public Response setSpeed(Request request) {
        String raw = request.param(PARAM_TICK_MILLIS);
        long tickMillis;
        try {
            tickMillis = Long.parseLong(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return Response.error(request.getId(), "tickMillis must be a whole number, got: " + raw);
        }
        return awaitSummary(request, id -> setSpeed.execute(id, tickMillis));
    }

    public Response snapshot(Request request) {
        String gameId = request.param(PARAM_GAME_ID);
        if (gameId == null || gameId.isBlank()) {
            return Response.error(request.getId(), "gameId is required");
        }
        try {
            GameSnapshot snapshot = await(getSnapshot.execute(gameId));
            return Response.ok(request.getId(), SnapshotMapper.toDto(snapshot));
        } catch (SessionNotFoundException e) {
            return Response.error(request.getId(), e.getMessage());
        } catch (ControllerFailure e) {
            return Response.error(request.getId(), e.getMessage());
        }
    }

    /** Shared shape for every command that names a game and answers with its new summary. */
    private Response awaitSummary(Request request,
                                  java.util.function.Function<String,
                                          CompletableFuture<SessionSummary>> action) {
        String gameId = request.param(PARAM_GAME_ID);
        if (gameId == null || gameId.isBlank()) {
            return Response.error(request.getId(), "gameId is required");
        }
        try {
            SessionSummary summary = await(action.apply(gameId));
            return Response.ok(request.getId(), SnapshotMapper.toDto(summary));
        } catch (SessionNotFoundException e) {
            return Response.error(request.getId(), e.getMessage());
        } catch (ControllerFailure e) {
            return Response.error(request.getId(), e.getMessage());
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(SESSION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Restore the flag so the pool can still see it was asked to stop.
            Thread.currentThread().interrupt();
            throw new ControllerFailure("Interrupted while waiting for the game to respond");
        } catch (TimeoutException e) {
            throw new ControllerFailure("The game did not respond within "
                    + SESSION_TIMEOUT_MILLIS + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof SessionNotFoundException notFound) {
                throw notFound;
            }
            throw new ControllerFailure(cause.getMessage() == null
                    ? cause.getClass().getSimpleName() : cause.getMessage());
        }
    }

    /** Internal signal that a command could not be completed; converted to an error response. */
    private static final class ControllerFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ControllerFailure(String message) {
            super(message);
        }
    }
}
