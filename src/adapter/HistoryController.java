package adapter;

import app.usecase.GetHistoryUseCase;
import app.usecase.GetLeaderboardUseCase;
import shared.Request;
import shared.Response;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Handles the commands answered from the database tier rather than from memory.
 *
 * <h2>Why a third controller</h2>
 * {@code LobbyController} speaks for the server as a whole and {@code GameController} for one
 * game; both answer from the {@code SessionRegistry}. These two commands answer from storage,
 * which gives them a different failure mode — the database process can be absent while the
 * server is perfectly healthy — and a different lifetime, since they describe games whose
 * sessions no longer exist. Folding them into the lobby would put two sources of truth behind
 * one class and make "why is this list empty" a question with two unrelated answers.
 *
 * <h2>Why an empty list is not an error</h2>
 * With {@code GameRepository.NO_OP} — no database tier attached — both use cases return
 * nothing, and this controller reports that as a successful empty result. That is deliberate:
 * a client asking for history when no history is kept has not made a mistake, and a GUI tab
 * that showed an error dialogue every time it refreshed against a server started with
 * {@code --db=off} would be unusable.
 */
public final class HistoryController {

    private static final String PARAM_LIMIT = "limit";

    private final GetHistoryUseCase getHistory;
    private final GetLeaderboardUseCase getLeaderboard;

    public HistoryController(GetHistoryUseCase getHistory, GetLeaderboardUseCase getLeaderboard) {
        this.getHistory = getHistory;
        this.getLeaderboard = getLeaderboard;
    }

    public Response history(Request request) {
        int limit;
        try {
            limit = Integer.parseInt(request.paramOrDefault(PARAM_LIMIT,
                    String.valueOf(GetHistoryUseCase.DEFAULT_LIMIT)).trim());
        } catch (NumberFormatException e) {
            return Response.error(request.getId(),
                    "limit must be a whole number, got: " + request.param(PARAM_LIMIT));
        }
        // ArrayList for the same reason LobbyController.list() uses one: the payload has to be
        // serialisable, and the mapper's list type is not part of the wire contract.
        Serializable payload =
                new ArrayList<>(SnapshotMapper.toFinishedDtos(getHistory.execute(limit)));
        return Response.ok(request.getId(), payload);
    }

    public Response leaderboard(Request request) {
        Serializable payload =
                new ArrayList<>(SnapshotMapper.toRankingDtos(getLeaderboard.execute()));
        return Response.ok(request.getId(), payload);
    }
}
