package app;

import app.model.GameSnapshot;
import app.model.PieceView;
import app.model.PlayerView;
import app.model.SessionState;
import engine.GameEngine;
import model.GameConstants;
import model.MysteryCell;
import model.Piece;
import player.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Photographs a live {@link GameEngine} into an immutable {@link GameSnapshot}.
 *
 * <p><b>Must be called from the session's own thread.</b> It walks mutable domain objects, so
 * running it concurrently with a round would produce a torn picture — half the pieces from
 * before a capture and half from after. Confining it to the session actor is what makes the
 * result trustworthy, and is cheaper than locking the board on every read.
 */
public final class SnapshotFactory {

    private SnapshotFactory() {
        // Static factory; not instantiable.
    }

    public static GameSnapshot capture(String gameId, GameEngine engine, SessionState state) {
        List<PlayerView> players = new ArrayList<>();
        for (AbstractPlayer player : engine.getPlayers()) {
            players.add(toPlayerView(player, engine));
        }

        MysteryCell mystery = engine.getBoard().getMysteryCell();
        boolean mysteryActive = mystery != null && mystery.isActive();

        return new GameSnapshot(
                gameId,
                engine.getGameMode().name(),
                state,
                engine.getRoundNumber(),
                players,
                mysteryActive ? mystery.getPosition() : GameConstants.NO_POSITION,
                mysteryActive ? mystery.getRoundsRemaining() : 0,
                toColourNames(engine.getFinishingOrder()),
                engine.isGameOver());
    }

    private static PlayerView toPlayerView(AbstractPlayer player, GameEngine engine) {
        List<PieceView> pieces = new ArrayList<>();
        int home = 0;
        int captures = 0;
        for (Piece piece : player.getPieces()) {
            pieces.add(toPieceView(piece));
            if (piece.getState().hasFinished()) {
                home++;
            }
            captures += piece.getCaptureCount();
        }

        return new PlayerView(
                player.getColour().name(),
                player.getName(),
                strategyName(player),
                pieces,
                player.getPiecesOnBoard().size(),
                player.getPiecesAtBase().size(),
                home,
                captures,
                engine.getFinishingOrder().contains(player));
    }

    private static PieceView toPieceView(Piece piece) {
        return new PieceView(
                piece.getId(),
                piece.getColour().name(),
                piece.getState().name(),
                piece.getPosition(),
                piece.getHomeStraightPosition(),
                piece.getDirection().name(),
                piece.getActiveEffect().name(),
                piece.getEffectRoundsLeft(),
                piece.getCaptureCount());
    }

    private static String strategyName(AbstractPlayer player) {
        return player.getStrategy() == null
                ? "NONE"
                : player.getStrategy().getClass().getSimpleName();
    }

    private static List<String> toColourNames(List<AbstractPlayer> players) {
        List<String> names = new ArrayList<>();
        for (AbstractPlayer player : players) {
            names.add(player.getColour().name());
        }
        return names;
    }
}
