package logger;

import enums.Direction;
import enums.TeleportDest;
import model.Block;
import model.MysteryCell;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

/**
 * Observer interface for game events. All methods have empty default implementations
 * so that new listeners only need to override the events they care about (ISP).
 *
 * @see GameEventPublisher
 * @see Logger
 */
public interface GameEventListener {

    default void onGameStart() { }

    /** Initial selection roll (before first player is determined): "[colour] rolls [value]" */
    default void onSelectionRoll(AbstractPlayer player, int value) { }

    /** In-game dice roll: "[colour] player rolled [value]." */
    default void onRoll(AbstractPlayer player, int value) { }

    /** Standard-board or home-straight move. {@code steps} = cells actually moved. */
    default void onMove(Piece piece, int from, int to, Direction direction, int steps) { }

    /** Piece was stopped before a blockade: "[colour] piece [id] is blocked from [from] to [blockedAt] by [blocker]." */
    default void onPieceBlocked(Piece piece, int from, int blockedAt, List<Piece> blockers) { }

    /** "[colour] piece [name] lands on square L1, captures [colour] piece [name], and returns it to the base." */
    default void onCapture(Piece attacker, Piece victim) { }

    /** "[colour] player now has N/4 on pieces on the board and N/4 pieces on the base." — printed after a capture resets the victim. */
    default void onCapturedPlayerStatus(AbstractPlayer player) { }

    /** "[colour] player wins!!!" */
    default void onWin(AbstractPlayer player, int place) { }

    default void onRoundComplete() { }

    /** "A mystery cell has spawned in location L1 and will be at this location for the next four rounds." */
    default void onMysterySpawn(int position) { }

    default void onBlockFormed(Block block) { }

    /** Coin toss when piece first enters the board. */
    default void onCoinToss(Piece piece, Direction direction) { }

    /** "The [colour] piece [name], which was moving clockwise, has changed to moving counterclockwise." */
    default void onDirectionChange(Piece piece, Direction from, Direction to) { }

    /** CCW piece teleported to Gamma redirected to Beta. */
    default void onGammaCCWTeleportToBeta(Piece piece) { }

    /** "[colour] piece [name] attends briefing and cannot move for four rounds." */
    default void onPieceFrozen(Piece piece) { }

    /** "[colour] piece [name] is movement-restricted and has rolled three consecutively." */
    default void onFrozenEscapeToBase(Piece piece) { }

    /** "[colour] piece [name] feels energized, and movement speed doubles." */
    default void onPieceEnergised(Piece piece) { }

    /** "[colour] piece [name] feels sick, and movement speed halves." */
    default void onPieceSick(Piece piece) { }

    /** "[colour] does not have other pieces… Moved the piece to square L3…" */
    default void onPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) { }

    /** "[colour] does not have other pieces… Ignoring the throw…" */
    default void onNoValidMove(AbstractPlayer player) { }

    /** End-of-round status summary per player and mystery cell. */
    default void onRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell) { }

    /** "[colour] player has four (04) pieces named…" at game start. */
    default void onGameInitialisation(List<AbstractPlayer> players) { }

    /** First player determined; includes final turn order. */
    default void onFirstPlayerSelected(AbstractPlayer player,
                                        List<AbstractPlayer> players,
                                        List<Integer> rolls) { }

    /** "[colour] player moves piece [id] to the starting point." + board/base counts. */
    default void onPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) { }

    /** Block auto-breaks at approach cell. */
    default void onBlockBrokenAtApproach(Block block, int approachCell) { }

    /** Attacker block captures defender block. */
    default void onBlockCapture(Block attacker, Block defender) { }

    /** Final standings after game ends. */
    default void onGameResult(List<AbstractPlayer> finishingOrder) { }

    /** "[colour] player lands on a mystery cell and is teleported to [dest]." + "[colour] piece [id] teleported to [dest]." */
    default void onTeleport(Piece piece, TeleportDest dest, int targetCell) { }
}
