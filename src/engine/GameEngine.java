package engine;

import enums.GameMode;
import enums.PieceEffect;
import enums.PieceState;
import enums.TeleportDest;
import logger.Logger;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.MysteryCell;
import model.Piece;
import model.RandomInitiator;
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
        AbstractPlayer firstPlayer = players.getFirst();
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
            board.setMysteryCell(new MysteryCell(GameConstants.NO_POSITION));
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
        board.removePiece(piece, piece.getPosition());
        piece.setPosition(result.getTargetCell());
        board.placePiece(piece, result.getTargetCell());
        piece.setState(PieceState.ACTIVE);

        logger.logMove(piece, piece.getPosition(), result.getTargetCell(), piece.getDirection());

        if (result.isCapture()) {
            handleCapture(piece, result);
        }

        if (result.isHome()) {
            handleHome(piece);
        }

        if (result.getTeleportDest() != null) {
            handleTeleport(piece, result);
        }
    }

    private void handleCapture(Piece piece, MoveResult result) {
        Piece capturedPiece = result.getCapturedPiece();
        logger.logCapture(piece, capturedPiece);
        piece.capture();
        board.removePiece(capturedPiece, capturedPiece.getPosition());
        capturedPiece.reset();
        turnManager.grantExtraRoll();
    }

    private void handleHome(Piece piece) {
        board.removePiece(piece, piece.getPosition());
        piece.setState(PieceState.HOME);
    }

    private void handleTeleport(Piece piece, MoveResult result) {
        PieceEffect effect = resolveTeleportEffect(result.getTeleportDest());
        applyTeleportDestination(piece, result.getTeleportDest());
        piece.applyEffect(effect);
    }

    private void applyTeleportDestination(Piece piece, TeleportDest dest) {
        switch (dest) {
            case BASE:
                board.removePiece(piece, piece.getPosition());
                piece.reset();
                break;
            case START_X:
                movePieceToCell(piece, board.getStartX(piece.getColour()));
                break;
            case APPROACH:
                movePieceToCell(piece, board.getApproach(piece.getColour()));
                break;
            default:
                movePieceToCell(piece, resolveTeleportCell(dest));
                break;
        }
    }

    private void movePieceToCell(Piece piece, int cellId) {
        board.removePiece(piece, piece.getPosition());
        piece.setPosition(cellId);
        board.placePiece(piece, cellId);
    }

    private int resolveTeleportCell(TeleportDest dest) {
        return switch (dest) {
            case ALPHA -> GameConstants.ALPHA_CELL;
            case BETA -> GameConstants.BETA_CELL;
            case GAMMA -> GameConstants.GAMMA_CELL;
            default -> GameConstants.NO_POSITION;
        };
    }

    private PieceEffect resolveTeleportEffect(TeleportDest dest) {
        return switch (dest) {
            case ALPHA -> resolveAlphaEffect();
            case BETA -> PieceEffect.FROZEN;
            case GAMMA -> PieceEffect.DIR_FLIP;
            default -> PieceEffect.NONE;
        };
    }

    private PieceEffect resolveAlphaEffect() {
        PieceEffect[] alphaEffects = {PieceEffect.ENERGISED, PieceEffect.SICK};
        return alphaEffects[RandomInitiator.getInstance().nextInt(alphaEffects.length)];
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