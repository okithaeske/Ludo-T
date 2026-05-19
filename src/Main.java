import engine.GameEngine;
import enums.GameMode;

public class Main {

    public static void main(String[] args) {
        GameEngine game = new GameEngine(GameMode.CLASSIC);
        game.startGame();
    }
}