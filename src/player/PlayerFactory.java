package player;

import enums.Colour;

import java.util.ArrayList;
import java.util.List;

public class PlayerFactory {

    public static List<AbstractPlayer> createPlayers() {
        List<AbstractPlayer> players = new ArrayList<>();
        players.add(createPlayer(Colour.RED));
        players.add(createPlayer(Colour.GREEN));
        players.add(createPlayer(Colour.YELLOW));
        players.add(createPlayer(Colour.BLUE));
        return players;
    }

    public static AbstractPlayer createPlayer(Colour colour) {
        return switch (colour) {
            case RED -> new RedPlayer();
            case GREEN -> new GreenPlayer();
            case YELLOW -> new YellowPlayer();
            case BLUE -> new BluePlayer();
            default -> throw new IllegalArgumentException("Cannot create player for colour: " + colour);
        };
    }
}