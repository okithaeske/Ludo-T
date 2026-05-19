package engine;

import enums.GameMode;
import enums.PieceState;
import logger.Logger;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;
import player.AbstractPlayer;
import player.PlayerFactory;

import java.util.List;

public class GameEngine {

    private final Board board;
    private final TurnManager turnManager;
    private final RuleEngine ruleEngine;
    private final List<AbstractPlayer> players;
    private final Logger logger;
    private int roundNumber;
    private boolean gameOver;
    private final GameMode gameMode;

    public GameEngine(GameMode gameMode) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.logger = new Logger();
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players);
        this.roundNumber = 0;
        this.gameOver = false;
    }

    public void startGame() {
        logger.logGameStart();
        initPlayers();
        determineFirstPlayer();
        runGameLoop();
    }

    public void initPlayers() {
        if (isLudoT()) {
            initialiseLudoTPlayerState();
        }
    }

    private void initialiseLudoTPlayerState() {
        for (AbstractPlayer player : players) {
            for (Piece piece : player.getPieces()) {
                piece.setApproachPassCount(0);
            }
        }
    }

    public void determineFirstPlayer() {
        AbstractPlayer firstPlayer = rollForFirstPlayer();
        turnManager.setOrder(firstPlayer);
    }

    private AbstractPlayer rollForFirstPlayer() {
        AbstractPlayer firstPlayer = players.get(0);
        int highestRoll = 0;
        for (AbstractPlayer player : players) {
            int roll = turnManager.rollDice();
            logger.logRoll(player, roll);
            if (roll > highestRoll) {
                highestRoll = roll;
                firstPlayer = player;
            }
        }
        return firstPlayer;
    }

    public void runGameLoop() {
        while (isGameNotOver()) {
            executeRound();
        }
    }

    private boolean isGameNotOver() {
        return gameOver == false;
    }

    public void executeRound() {
        roundNumber++;
        handleMysteryCell();

        for (AbstractPlayer player : players) {
            executeTurn(player);
        }

        logger.logRoundStatus();
        checkWinCondition();
    }

    private void handleMysteryCell() {
        if (isLudoT() && isMysterySpawnRound()) {
            spawnOrRelocateMysteryCell();
        }
    }

    private boolean isMysterySpawnRound() {
        return roundNumber >= GameConstants.MYSTERY_SPAWN_ROUND;
    }

    private void spawnOrRelocateMysteryCell() {
        if (board.getMysteryCell() == null) {
            board.setMysteryCell(new model.MysteryCell(GameConstants.NO_POSITION));
            board.getMysteryCell().spawn(board);
        } else {
            board.getMysteryCell().tick();
        }
        logger.logMysterySpawn(board.getMysteryPosition());
    }

    private void executeTurn(AbstractPlayer player) {
        int roll = turnManager.rollDice();
        logger.logRoll(player, roll);

        if (turnManager.isExtraRollPending()) {
            turnManager.handleTripleSix();
            return;
        }

        Piece chosenPiece = player.choosePiece(roll, board);
        if (chosenPiece == null) {
            turnManager.nextPlayer();
            return;
        }

        MoveResult result = ruleEngine.validateMove(chosenPiece, roll);
        if (result.isValid()) {
            applyMove(chosenPiece, result, player);
        }

        handleExtraRoll(result);
        turnManager.nextPlayer();
    }

    private void applyMove(Piece piece, MoveResult result, AbstractPlayer player) {
        logger.logMove(piece, piece.getPosition(), result.getTargetCell(), piece.getDirection());
        piece.setPosition(result.getTargetCell());

        if (result.isCapture()) {
            handleCapture(piece, result, player);
        }

        if (result.isHome()) {
            piece.setState(PieceState.HOME);
        }

        if (result.getTeleportDest() != null) {
            handleTeleport(piece, result);
        }
    }

    private void handleCapture(Piece piece, MoveResult result, AbstractPlayer player) {
        Piece capturedPiece = result.getCapturedPiece();
        logger.logCapture(piece, capturedPiece);
        piece.capture();
        capturedPiece.reset();
        turnManager.grantExtraRoll();
    }

    private void handleTeleport(Piece piece, MoveResult result) {
        piece.applyEffect(
                resolveTeleportEffect(result.getTeleportDest())
        );
    }

    private enums.PieceEffect resolveTeleportEffect(enums.TeleportDest dest) {
        switch (dest) {
            case ALPHA:
                return resolveAlphaEffect();
            case BETA:
                return enums.PieceEffect.FROZEN;
            case GAMMA:
                return enums.PieceEffect.DIR_FLIP;
            default:
                return enums.PieceEffect.NONE;
        }
    }

    private enums.PieceEffect resolveAlphaEffect() {
        int srand = model.RandomInitiator.getInstance().nextInt(2);
        if (srand == 0) {
            return enums.PieceEffect.ENERGISED;
        }
        return enums.PieceEffect.SICK;
    }

    private void handleExtraRoll(MoveResult result) {
        if (result.isCapture()) {
            turnManager.grantExtraRoll();
        }
    }

    public boolean checkWinCondition() {
        for (AbstractPlayer player : players) {
            if (player.allHome()) {
                logger.logWinner(player);
                gameOver = true;
                return true;
            }
        }
        return false;
    }

    public boolean isLudoT() {
        return gameMode == GameMode.LUDO_T;
    }
}