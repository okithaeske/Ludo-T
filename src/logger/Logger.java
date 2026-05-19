package logger;

import model.Block;
import model.Piece;
import enums.Direction;
import player.AbstractPlayer;

public class Logger {

    public void logGameStart() {
        System.out.println("Game started");
    }

    public void logRoll(AbstractPlayer player, int value) {
        System.out.println(player.getName() + " rolled " + value);
    }

    public void logMove(Piece piece, int from, int to, Direction direction) {
        System.out.println(piece.getId() + " moved from " + from + " to " + to + " direction " + direction);
    }

    public void logCapture(Piece attacker, Piece victim) {
        System.out.println(attacker.getId() + " captured " + victim.getId());
    }

    public void logWinner(AbstractPlayer player) {
        System.out.println(player.getName() + " wins!");
    }

    public void logRoundStatus() {
        System.out.println("Round complete");
    }

    public void logMysterySpawn(int position) {
        System.out.println("Mystery cell spawned at " + position);
    }

    public void logBlockFormed(Block block) {
        System.out.println("Block formed at " + block.getPosition() + " size " + block.getSize());
    }
}