package logger;

import enums.Direction;
import enums.TeleportDest;
import model.Block;
import model.MysteryCell;
import model.Piece;
import player.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class GameEventPublisher {

    private final List<GameEventListener> listeners = new ArrayList<>();

    public void addListener(GameEventListener listener) { listeners.add(listener); }
    public void removeListener(GameEventListener listener) { listeners.remove(listener); }

    public void publishGameStart() {
        for (GameEventListener l : listeners) l.onGameStart();
    }

    /** Initial selection roll (before first player chosen). */
    public void publishSelectionRoll(AbstractPlayer player, int value) {
        for (GameEventListener l : listeners) l.onSelectionRoll(player, value);
    }

    /** In-game dice roll. */
    public void publishRoll(AbstractPlayer player, int value) {
        for (GameEventListener l : listeners) l.onRoll(player, value);
    }

    /** @param steps cells actually moved (effective roll, accounting for energised/sick). */
    public void publishMove(Piece piece, int from, int to, Direction direction, int steps) {
        for (GameEventListener l : listeners) l.onMove(piece, from, to, direction, steps);
    }

    public void publishPieceBlocked(Piece piece, int from, int blockedAt, List<Piece> blockers) {
        for (GameEventListener l : listeners) l.onPieceBlocked(piece, from, blockedAt, blockers);
    }

    public void publishCapture(Piece attacker, Piece victim) {
        for (GameEventListener l : listeners) l.onCapture(attacker, victim);
    }

    public void publishWin(AbstractPlayer player, int place) {
        for (GameEventListener l : listeners) l.onWin(player, place);
    }

    public void publishRoundComplete() {
        for (GameEventListener l : listeners) l.onRoundComplete();
    }

    public void publishMysterySpawn(int position) {
        for (GameEventListener l : listeners) l.onMysterySpawn(position);
    }

    public void publishBlockFormed(Block block) {
        for (GameEventListener l : listeners) l.onBlockFormed(block);
    }

    public void publishCoinToss(Piece piece, Direction direction) {
        for (GameEventListener l : listeners) l.onCoinToss(piece, direction);
    }

    public void publishDirectionChange(Piece piece, Direction from, Direction to) {
        for (GameEventListener l : listeners) l.onDirectionChange(piece, from, to);
    }

    public void publishGammaCCWTeleportToBeta(Piece piece) {
        for (GameEventListener l : listeners) l.onGammaCCWTeleportToBeta(piece);
    }

    public void publishPieceFrozen(Piece piece) {
        for (GameEventListener l : listeners) l.onPieceFrozen(piece);
    }

    public void publishFrozenEscapeToBase(Piece piece) {
        for (GameEventListener l : listeners) l.onFrozenEscapeToBase(piece);
    }

    public void publishPieceEnergised(Piece piece) {
        for (GameEventListener l : listeners) l.onPieceEnergised(piece);
    }

    public void publishPieceSick(Piece piece) {
        for (GameEventListener l : listeners) l.onPieceSick(piece);
    }

    public void publishPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) {
        for (GameEventListener l : listeners) l.onPieceBlockedAtAdjacent(piece, blockedAt, movedTo);
    }

    public void publishNoValidMove(AbstractPlayer player) {
        for (GameEventListener l : listeners) l.onNoValidMove(player);
    }

    public void publishRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell) {
        for (GameEventListener l : listeners) l.onRoundSummary(players, mysteryCell);
    }

    public void publishGameInitialisation(List<AbstractPlayer> players) {
        for (GameEventListener l : listeners) l.onGameInitialisation(players);
    }

    public void publishFirstPlayerSelected(AbstractPlayer player,
                                            List<AbstractPlayer> players,
                                            List<Integer> rolls) {
        for (GameEventListener l : listeners) l.onFirstPlayerSelected(player, players, rolls);
    }

    public void publishPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) {
        for (GameEventListener l : listeners) l.onPieceMoveToX(piece, piecesOnBoard, piecesAtBase);
    }

    public void publishBlockBrokenAtApproach(Block block, int approachCell) {
        for (GameEventListener l : listeners) l.onBlockBrokenAtApproach(block, approachCell);
    }

    public void publishBlockCapture(Block attacker, Block defender) {
        for (GameEventListener l : listeners) l.onBlockCapture(attacker, defender);
    }

    public void publishGameResult(List<AbstractPlayer> finishingOrder) {
        for (GameEventListener l : listeners) l.onGameResult(finishingOrder);
    }

    public void publishTeleport(Piece piece, TeleportDest dest, int targetCell) {
        for (GameEventListener l : listeners) l.onTeleport(piece, dest, targetCell);
    }
}
