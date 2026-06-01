package engine;

import logger.GameEventPublisher;
import model.GameConstants;
import player.AbstractPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the finishing order and signals game-over.
 *
 * <p>{@link #checkWinCondition(player.AbstractPlayer)} is called after every turn by
 * {@link GameEngine#executeRound()}. When enough players have finished,
 * {@link #isGameOver()} returns {@code true} and the game loop in
 * {@link GameEngine#runGameLoop()} exits.
 *
 * @see GameEngine#executeRound()
 */
public class WinTracker {

    private final List<AbstractPlayer> players;
    private final GameEventPublisher publisher;
    private final List<AbstractPlayer> finishingOrder = new ArrayList<>();
    private boolean gameOver;

    public WinTracker(List<AbstractPlayer> players, GameEventPublisher publisher) {
        this.players = players;
        this.publisher = publisher;
        this.gameOver = false;
    }

    /**
     * Checks if {@code justMoved} has all pieces home. If so, adds them to the finishing
     * order and publishes a win event. Sets {@link #isGameOver()} when the required number
     * of finishers is reached ({@link model.GameConstants#FINISHING_PLAYERS_TO_END}).
     *
     * @return {@code true} if the game is now over (caller should stop the round immediately).
     * @see GameEngine#executeRound()
     */
    public boolean checkWinCondition(AbstractPlayer justMoved) {
        if (justMoved.allHome() && !finishingOrder.contains(justMoved)) {
            finishingOrder.add(justMoved);
            publisher.publishWin(justMoved, finishingOrder.size());
            if (finishingOrder.size() >= GameConstants.FINISHING_PLAYERS_TO_END) {
                for (AbstractPlayer player : players) {
                    if (!finishingOrder.contains(player)) {
                        finishingOrder.add(player);
                        break;
                    }
                }
                gameOver = true;
                return true;
            }
        }
        return false;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public List<AbstractPlayer> getFinishingOrder() {
        return finishingOrder;
    }
}
