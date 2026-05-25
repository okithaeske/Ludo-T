package logger;

import enums.Direction;
import model.Block;
import model.Piece;
import player.AbstractPlayer;

public interface GameEventListener {
    void onGameStart();
    void onRoll(AbstractPlayer player, int value);
    void onMove(Piece piece, int from, int to, Direction direction);
    void onCapture(Piece attacker, Piece victim);
    void onWin(AbstractPlayer player);
    void onRoundComplete();
    void onMysterySpawn(int position);
    void onBlockFormed(Block block);
}