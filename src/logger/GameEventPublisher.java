package logger;

import enums.Direction;
import model.Block;
import model.Piece;
import player.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

public class GameEventPublisher {

    private final List<GameEventListener> listeners = new ArrayList<>();

    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    public void publishGameStart() {
        for (GameEventListener listener : listeners) {
            listener.onGameStart();
        }
    }

    public void publishRoll(AbstractPlayer player, int value) {
        for (GameEventListener listener : listeners) {
            listener.onRoll(player, value);
        }
    }

    public void publishMove(Piece piece, int from, int to, Direction direction) {
        for (GameEventListener listener : listeners) {
            listener.onMove(piece, from, to, direction);
        }
    }

    public void publishCapture(Piece attacker, Piece victim) {
        for (GameEventListener listener : listeners) {
            listener.onCapture(attacker, victim);
        }
    }

    public void publishWin(AbstractPlayer player) {
        for (GameEventListener listener : listeners) {
            listener.onWin(player);
        }
    }

    public void publishRoundComplete() {
        for (GameEventListener listener : listeners) {
            listener.onRoundComplete();
        }
    }

    public void publishMysterySpawn(int position) {
        for (GameEventListener listener : listeners) {
            listener.onMysterySpawn(position);
        }
    }

    public void publishBlockFormed(Block block) {
        for (GameEventListener listener : listeners) {
            listener.onBlockFormed(block);
        }
    }
}