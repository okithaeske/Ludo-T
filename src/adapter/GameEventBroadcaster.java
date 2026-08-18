package adapter;

import app.port.EventSink;
import app.port.SessionEvent;
import enums.Direction;
import enums.TeleportDest;
import logger.GameEventListener;
import logger.Logger;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

/**
 * Turns domain events into log lines and pushes them at an {@link EventSink}.
 *
 * <h2>Why this class is the centrepiece of the architecture</h2>
 * {@code logger.GameEventListener} was <em>already</em> an output port in Assignment 1, with
 * {@link Logger} as its only implementation. This class is a second implementation, registered
 * in exactly the place {@code Logger} used to sit. The engine calls
 * {@code publisher.publishCapture(...)} precisely as before and has no idea the event now
 * leaves the process over a socket. Not one line of {@code GameEngine},
 * {@code GameEventPublisher} or any other engine class changed to make the game
 * network-visible — which is what "the domain does not depend on the delivery mechanism"
 * means in practice rather than in a diagram.
 *
 * <h2>Why the wording differs from {@code Logger}</h2>
 * {@code Logger} emits the assignment's verbose, spec-exact console lines and still does, on
 * the CLI path. A scrolling GUI panel has different needs, so these lines are short and
 * scannable. Same events, two presentations — which is the point of having the port.
 *
 * <h2>Threading</h2>
 * Every callback arrives on the owning session's actor thread, so this class needs no
 * synchronisation of its own. It must not block: see {@link EventSink}.
 */
public final class GameEventBroadcaster implements GameEventListener {

    private final String gameId;
    private final EventSink sink;

    public GameEventBroadcaster(String gameId, EventSink sink) {
        this.gameId = gameId;
        this.sink = sink;
    }

    private void emit(String message) {
        sink.publish(SessionEvent.log(gameId, message));
    }

    private static String colour(Piece piece) {
        return piece.getColour().name().toLowerCase();
    }

    private static String colour(AbstractPlayer player) {
        return player.getColour().name().toLowerCase();
    }

    private static String direction(Direction d) {
        return d == Direction.CW ? "CW" : "CCW";
    }

    @Override
    public void onGameStart() {
        emit("Game started.");
    }

    @Override
    public void onGameInitialisation(List<AbstractPlayer> players) {
        for (AbstractPlayer player : players) {
            emit(colour(player) + " enters with 4 pieces ("
                    + player.getStrategy().getClass().getSimpleName() + ").");
        }
    }

    @Override
    public void onSelectionRoll(AbstractPlayer player, int value) {
        emit(colour(player) + " rolls " + value + " for order.");
    }

    @Override
    public void onFirstPlayerSelected(AbstractPlayer player, List<AbstractPlayer> players,
                                      List<Integer> rolls) {
        StringBuilder order = new StringBuilder();
        for (AbstractPlayer p : players) {
            if (order.length() > 0) {
                // ASCII rather than an arrow glyph: these lines are read in a Windows console
                // as well as the GUI, and cp1252 renders "→" as a question mark.
                order.append(" -> ");
            }
            order.append(colour(p));
        }
        emit(colour(player) + " goes first. Order: " + order + ".");
    }

    @Override
    public void onRoll(AbstractPlayer player, int value) {
        emit(colour(player) + " rolled " + value + ".");
    }

    @Override
    public void onPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) {
        emit(colour(piece) + " " + piece.getId() + " enters the board ("
                + piecesOnBoard + " out, " + piecesAtBase + " at base).");
    }

    @Override
    public void onMove(Piece piece, int from, int to, Direction direction, int steps) {
        emit(colour(piece) + " " + piece.getId() + " " + from + " -> " + to
                + " (" + steps + " " + direction(direction) + ").");
    }

    @Override
    public void onCapture(Piece attacker, Piece victim) {
        emit(colour(attacker) + " " + attacker.getId() + " captures "
                + colour(victim) + " " + victim.getId()
                + " at " + attacker.getPosition() + ".");
    }

    @Override
    public void onPieceBlocked(Piece piece, int from, int blockedAt, List<Piece> blockers) {
        emit(colour(piece) + " " + piece.getId() + " blocked at " + blockedAt + ".");
    }

    @Override
    public void onPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) {
        emit(colour(piece) + " " + piece.getId() + " stops at " + movedTo
                + ", before the block at " + blockedAt + ".");
    }

    @Override
    public void onNoValidMove(AbstractPlayer player) {
        emit(colour(player) + " has no legal move; turn skipped.");
    }

    @Override
    public void onCoinToss(Piece piece, Direction direction) {
        emit(colour(piece) + " " + piece.getId() + " will travel "
                + direction(direction) + " (coin toss).");
    }

    @Override
    public void onDirectionChange(Piece piece, Direction from, Direction to) {
        emit(colour(piece) + " " + piece.getId() + " flips "
                + direction(from) + " -> " + direction(to) + ".");
    }

    @Override
    public void onMysterySpawn(int position) {
        emit("Mystery cell appears at " + position + ".");
    }

    @Override
    public void onTeleport(Piece piece, TeleportDest dest, int targetCell) {
        emit(colour(piece) + " " + piece.getId() + " teleports to " + dest.name() + ".");
    }

    @Override
    public void onGammaCCWTeleportToBeta(Piece piece) {
        emit(colour(piece) + " " + piece.getId() + " is CCW; Gamma redirected to Beta.");
    }

    @Override
    public void onPieceFrozen(Piece piece) {
        emit(colour(piece) + " " + piece.getId() + " is frozen for four rounds.");
    }

    @Override
    public void onFrozenEscapeToBase(Piece piece) {
        emit(colour(piece) + " " + piece.getId() + " escapes the freeze — sent to base.");
    }

    @Override
    public void onPieceEnergised(Piece piece) {
        emit(colour(piece) + " " + piece.getId() + " is energised (speed x2).");
    }

    @Override
    public void onPieceSick(Piece piece) {
        emit(colour(piece) + " " + piece.getId() + " is sick (speed halved).");
    }

    @Override
    public void onWin(AbstractPlayer player, int place) {
        emit(colour(player) + " finishes in place " + place + ".");
    }

    @Override
    public void onGameResult(List<AbstractPlayer> finishingOrder) {
        StringBuilder standings = new StringBuilder("Final standings: ");
        for (int i = 0; i < finishingOrder.size(); i++) {
            if (i > 0) {
                standings.append(", ");
            }
            standings.append(i + 1).append(") ").append(colour(finishingOrder.get(i)));
        }
        emit(standings.toString());
    }
}
