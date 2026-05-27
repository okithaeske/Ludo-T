package logger;

import enums.Direction;
import model.Block;
import model.MysteryCell;
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

    public void publishCoinToss(Piece piece, Direction direction) {
        for (GameEventListener listener : listeners) {
            listener.onCoinToss(piece, direction);
        }
    }

    public void publishDirectionChange(Piece piece, Direction from, Direction to) {
        for (GameEventListener listener : listeners) {
            listener.onDirectionChange(piece, from, to);
        }
    }

    public void publishGammaCCWTeleportToBeta(Piece piece) {
        for (GameEventListener listener : listeners) {
            listener.onGammaCCWTeleportToBeta(piece);
        }
    }

    public void publishPieceFrozen(Piece piece) {
        for (GameEventListener listener : listeners) {
            listener.onPieceFrozen(piece);
        }
    }

    public void publishFrozenEscapeToBase(Piece piece) {
        for (GameEventListener listener : listeners) {
            listener.onFrozenEscapeToBase(piece);
        }
    }

    public void publishPieceEnergised(Piece piece) {
        for (GameEventListener listener : listeners) {
            listener.onPieceEnergised(piece);
        }
    }

    public void publishPieceSick(Piece piece) {
        for (GameEventListener listener : listeners) {
            listener.onPieceSick(piece);
        }
    }

    public void publishPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) {
        for (GameEventListener listener : listeners) {
            listener.onPieceBlockedAtAdjacent(piece, blockedAt, movedTo);
        }
    }

    public void publishNoValidMove(AbstractPlayer player) {
        for (GameEventListener listener : listeners) {
            listener.onNoValidMove(player);
        }
    }

    public void publishRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell) {
        for (GameEventListener listener : listeners) {
            listener.onRoundSummary(players, mysteryCell);
        }
    }

    public void publishGameInitialisation(List<AbstractPlayer> players) {
        for (GameEventListener listener : listeners) {
            listener.onGameInitialisation(players);
        }
    }

    public void publishFirstPlayerSelected(AbstractPlayer player, List<AbstractPlayer> players, List<Integer> rolls) {
        for (GameEventListener listener : listeners) {
            listener.onFirstPlayerSelected(player, players, rolls);
        }
    }

    public void publishPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) {
        for (GameEventListener listener : listeners) {
            listener.onPieceMoveToX(piece, piecesOnBoard, piecesAtBase);
        }
    }
}
