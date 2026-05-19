import engine.GameEngine;
import enums.GameMode;
import model.RandomInitiator;

public class Main {

    public static void main(String[] args) {
        RandomInitiator.getInstance().setSeed(10);
        GameEngine game = new GameEngine(GameMode.CLASSIC);
        game.startGame();
    }
}