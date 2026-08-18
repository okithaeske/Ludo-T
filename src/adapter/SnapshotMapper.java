package adapter;

import app.model.GameSnapshot;
import app.model.PieceView;
import app.model.PlayerView;
import app.model.SessionSummary;
import shared.BoardSnapshot;
import shared.PieceDto;
import shared.PlayerDto;
import shared.SessionSummaryDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates application output models into wire DTOs.
 *
 * <p>The two vocabularies look alike, and collapsing them into one shared type would delete
 * this class. That would also make the application layer depend on the wire format, so a
 * protocol change — renaming a field, adding a version — would reach all the way in and force
 * the interactors to be recompiled and retested. Keeping the mapping here means the wire may
 * change freely and only this class notices; it is the layer whose job is precisely to know
 * both sides.
 *
 * <p>This is the only class in the codebase that imports both {@code app.model} and
 * {@code shared}.
 */
public final class SnapshotMapper {

    private SnapshotMapper() {
        // Static mapper; not instantiable.
    }

    public static BoardSnapshot toDto(GameSnapshot snapshot) {
        List<PlayerDto> players = new ArrayList<>(snapshot.players().size());
        for (PlayerView player : snapshot.players()) {
            players.add(toDto(player));
        }
        return new BoardSnapshot(
                snapshot.gameId(),
                snapshot.mode(),
                snapshot.state().name(),
                snapshot.round(),
                players,
                snapshot.mysteryPosition(),
                snapshot.mysteryRoundsRemaining(),
                snapshot.finishingOrder(),
                snapshot.gameOver());
    }

    public static PlayerDto toDto(PlayerView player) {
        List<PieceDto> pieces = new ArrayList<>(player.pieces().size());
        for (PieceView piece : player.pieces()) {
            pieces.add(toDto(piece));
        }
        return new PlayerDto(
                player.colour(),
                player.name(),
                player.strategy(),
                pieces,
                player.piecesOnBoard(),
                player.piecesAtBase(),
                player.piecesHome(),
                player.totalCaptures(),
                player.finished());
    }

    public static PieceDto toDto(PieceView piece) {
        return new PieceDto(
                piece.id(),
                piece.colour(),
                piece.state(),
                piece.position(),
                piece.homeStraightPosition(),
                piece.direction(),
                piece.effect(),
                piece.effectRoundsLeft(),
                piece.captureCount());
    }

    public static SessionSummaryDto toDto(SessionSummary summary) {
        return new SessionSummaryDto(
                summary.gameId(),
                summary.mode(),
                summary.seed(),
                summary.state().name(),
                summary.round(),
                summary.subscribers(),
                summary.tickMillis());
    }

    public static List<SessionSummaryDto> toDtos(List<SessionSummary> summaries) {
        List<SessionSummaryDto> dtos = new ArrayList<>(summaries.size());
        for (SessionSummary summary : summaries) {
            dtos.add(toDto(summary));
        }
        return dtos;
    }
}
