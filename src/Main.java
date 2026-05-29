import engine.GameEngine;
import engine.GameEngineBuilder;
import enums.GameMode;

public class Main {

    public static void main(String[] args) {
        GameEngine game = new GameEngineBuilder()
                .withMode(GameMode.LUDO_T)
                .build();
        game.startGame();
    }
}