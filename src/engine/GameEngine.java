package engine;

import enums.GameMode;
import enums.PieceEffect;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventPublisher;
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
    private final GameEventPublisher publisher;
    private int roundNumber;
    private boolean gameOver;
    private final GameMode gameMode;


    public GameEngine(GameMode gameMode) {
        this.gameMode = gameMode;
        this.board = new Board();
        this.publisher = new GameEventPublisher();
        this.publisher.addListener(new Logger());
        this.players = PlayerFactory.createPlayers();
        this.ruleEngine = new RuleEngine(board, gameMode);
        this.turnManager = new TurnManager(players);
        this.roundNumber = 0;
        this.gameOver = false;
    }

    public void startGame() {
        publisher.publishGameStart();
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
            publisher.publishRoll(player, roll);
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

        publisher.publishRoundComplete();
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
        publisher.publishMysterySpawn(board.getMysteryPosition());
    }

    private void executeTurn(AbstractPlayer player) {
        int roll = turnManager.rollDice();
        publisher.publishRoll(player, roll);

        if (turnManager.isTripleSix()) {
            turnManager.handleTripleSix();
            return;
        }

        if (isFrozenEscape(player, roll)) {
            handleFrozenEscape(player);
            return;
        }

        if (hasActiveFrozenPiece(player)) {
            tickFrozenPiece(player);
            turnManager.nextPlayer();
            return;
        }

        Piece chosenPiece = player.choosePiece(roll, board);
        if (chosenPiece.isNull()) {
            turnManager.nextPlayer();
            return;
        }

        MoveResult result = ruleEngine.validateMove(chosenPiece, roll);
        if (result.isValid()) {
            applyMove(chosenPiece, result, player);
        }

        handleExtraRoll(roll);
        turnManager.nextPlayer();
    }

    private void applyMove(Piece piece, MoveResult result, AbstractPlayer player) {
        int fromCell = piece.getPosition(); // save BEFORE updating

        board.removePiece(piece, piece.getPosition());
        piece.setPosition(result.getTargetCell());
        board.placePiece(piece, result.getTargetCell());
        piece.setState(PieceState.ACTIVE);

        publisher.publishMove(piece, fromCell, result.getTargetCell(), piece.getDirection());

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
        publisher.publishCapture(piece, capturedPiece);
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

    private void handleExtraRoll(int roll) {
        if (roll == GameConstants.MAX_DICE_ROLL) {
            turnManager.grantExtraRoll();
        }
    }

    public boolean checkWinCondition() {
        for (AbstractPlayer player : players) {
            if (player.allHome()) {
                publisher.publishWin(player);
                gameOver = true;
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveFrozenPiece(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.getActiveEffect() == PieceEffect.FROZEN
                    && piece.getEffectRoundsLeft() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isFrozenEscape(AbstractPlayer player, int roll) {
        return hasActiveFrozenPiece(player) && turnManager.isTripleThree();
    }

    private void handleFrozenEscape(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.getActiveEffect() == PieceEffect.FROZEN) {
                publisher.publishMove(piece, piece.getPosition(),
                        GameConstants.BASE_POSITION, piece.getDirection());
                board.removePiece(piece, piece.getPosition());
                piece.reset();
                break;
            }
        }
        turnManager.resetConsecutiveThrees();
        turnManager.nextPlayer();
    }

    private void tickFrozenPiece(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.getActiveEffect() == PieceEffect.FROZEN) {
                piece.setEffectRoundsLeft(piece.getEffectRoundsLeft() - 1);
                if (piece.getEffectRoundsLeft() <= 0) {
                    piece.applyEffect(PieceEffect.NONE);
                }
                break;
            }
        }
    }

    public boolean isLudoT() {
        return gameMode == GameMode.LUDO_T;
    }
}