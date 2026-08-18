package engine;

import logger.GameEventPublisher;
import model.Dice;
import player.AbstractPlayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FirstPlayerSelector {

    private final List<AbstractPlayer> players;
    private final TurnManager turnManager;
    private final GameEventPublisher publisher;
    private final Dice dice;

    public FirstPlayerSelector(List<AbstractPlayer> players, TurnManager turnManager,
                               GameEventPublisher publisher) {
        this.players = players;
        this.turnManager = turnManager;
        this.publisher = publisher;
        // Rolls from the same dice the turn manager uses, so the selection rolls and the
        // game's rolls come from one per-game stream rather than a process-wide singleton.
        this.dice = turnManager.getDice();
    }

    public AbstractPlayer selectFirstPlayer() {
        List<AbstractPlayer> candidates = rollForAllPlayers(players);
        while (candidates.size() > 1) {
            candidates = rollForAllPlayers(candidates);
        }
        return candidates.get(0);
    }

    /** Rolls once for each candidate, publishes each roll, and returns those who tied highest. */
    private List<AbstractPlayer> rollForAllPlayers(List<AbstractPlayer> candidates) {
        Map<AbstractPlayer, Integer> results = new LinkedHashMap<>();
        for (AbstractPlayer player : candidates) {
            int roll = dice.roll();
            results.put(player, roll);
            publisher.publishSelectionRoll(player, roll);
        }
        int highest = Collections.max(results.values());
        return results.entrySet().stream()
                .filter(e -> e.getValue() == highest)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
