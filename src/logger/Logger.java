package logger;

import enums.Direction;
import model.Block;
import model.Piece;
import player.AbstractPlayer;

public class Logger implements GameEventListener {

    @Override
    public void onGameStart() {
        System.out.println("Game started");
    }

    @Override
    public void onRoll(AbstractPlayer player, int value) {
        System.out.println(player.getName() + " rolled " + value);
    }

    @Override
    public void onMove(Piece piece, int from, int to, Direction direction) {
        System.out.println(piece.getId() + " moved from " + from + " to " + to + " direction " + direction);
    }

    @Override
    public void onCapture(Piece attacker, Piece victim) {
        System.out.println(attacker.getId() + " captured " + victim.getId());
    }

    @Override
    public void onWin(AbstractPlayer player) {
        System.out.println(player.getName() + " wins!");
    }

    @Override
    public void onRoundComplete() {
        System.out.println("Round complete");
    }

    @Override
    public void onMysterySpawn(int position) {
        System.out.println("Mystery cell spawned at " + position);
    }

    @Override
    public void onBlockFormed(Block block) {
        System.out.println("Block formed at " + block.getPosition() + " size " + block.getSize());
    }
}