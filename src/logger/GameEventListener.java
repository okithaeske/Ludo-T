package logger;

import enums.Direction;
import model.Block;
import model.MysteryCell;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

public interface GameEventListener {
    void onGameStart();
    void onRoll(AbstractPlayer player, int value);
    void onMove(Piece piece, int from, int to, Direction direction);
    void onCapture(Piece attacker, Piece victim);
    void onWin(AbstractPlayer player, int place);
    void onRoundComplete();
    void onMysterySpawn(int position);
    void onBlockFormed(Block block);

    // Coin toss when piece enters X: "[Color] piece [Name] direction determined as [cw/ccw] by coin toss."
    void onCoinToss(Piece piece, Direction direction);

    // Direction flip event: "The [Color] piece [name], which was moving clockwise, has changed to moving counterclockwise."
    void onDirectionChange(Piece piece, Direction from, Direction to);

    // CCW piece teleported to Gamma → redirected to Beta
    void onGammaCCWTeleportToBeta(Piece piece);

    // "[Color] piece [name] attends briefing and cannot move for four rounds."
    void onPieceFrozen(Piece piece);

    // "[Color] piece [name] is movement-restricted and has rolled three consecutively. Teleporting piece [name] to base."
    void onFrozenEscapeToBase(Piece piece);

    // "[Color] piece [name] feels energized, and movement speed doubles."
    void onPieceEnergised(Piece piece);

    // "[Color] piece [name] feels sick, and movement speed halves."
    void onPieceSick(Piece piece);

    // Piece stopped at cell before opponent block
    void onPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo);

    // No valid move available for player
    void onNoValidMove(AbstractPlayer player);

    // End-of-round status summary
    void onRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell);

    // Game initialisation: list pieces for each player
    void onGameInitialisation(List<AbstractPlayer> players);

    // First player selection rolls + order
    void onFirstPlayerSelected(AbstractPlayer player, List<AbstractPlayer> players, List<Integer> rolls);

    // Piece moved from base to X with board/base counts
    void onPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase);

    // Block auto-breaks when it reaches the approach cell
    void onBlockBrokenAtApproach(Block block, int approachCell);

    // Attacker block captures defender block
    void onBlockCapture(Block attacker, Block defender);
}
