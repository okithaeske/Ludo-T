package engine;

import enums.Direction;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        publisher.publishGameInitialisation(players);
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
        List<Integer> rolls = new ArrayList<>();
        for (AbstractPlayer player : players) {
            int roll = turnManager.rollDice();
            rolls.add(roll);
            publisher.publishRoll(player, roll);
            if (roll > highestRoll) {
                highestRoll = roll;
                firstPlayer = player;
            }
        }
        publisher.publishFirstPlayerSelected(firstPlayer, players, rolls);
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

        // Rule spec §3: publish round-end summary after all players have moved
        publisher.publishRoundSummary(players, board.getMysteryCell());
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

        // Rule T-6 (3.4): triple six with blockade forces the block to break
        if (turnManager.isTripleSix()) {
            if (isLudoT() && playerHasBlock(player)) {
                forceBlockBreak(player);
            }
            turnManager.handleTripleSix();
            return;
        }

        // Rule T-13 (3.2): frozen piece rolls three 3s consecutively — teleport to base
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
            publisher.publishNoValidMove(player);
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

        // Rule T-1 (3.1): coin toss when piece moves from base to X
        if (fromCell == GameConstants.BASE_POSITION) {
            performCoinToss(piece);
            publisher.publishPieceMoveToX(piece,
                    player.getPiecesOnBoard().size(),
                    player.getPiecesAtBase().size());
        } else {
            publisher.publishMove(piece, fromCell, result.getTargetCell(), piece.getDirection());
        }

        // Rule T-1 (4.2): track CCW approach cell passes for home entry
        if (isLudoT() && piece.getDirection() == Direction.CCW
                && !result.isHome() && fromCell != GameConstants.BASE_POSITION) {
            trackApproachPass(piece, fromCell, result.getTargetCell());
        }

        // Log blocked-at-adjacent event
        if (result.isBlockedAtAdjacent()) {
            publisher.publishPieceBlockedAtAdjacent(piece, result.getTargetCell() + 1, result.getTargetCell());
        }

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

    // Rule T-1 (3.1): coin toss to decide CW or CCW direction
    private void performCoinToss(Piece piece) {
        int toss = RandomInitiator.getInstance().nextInt(2);
        Direction direction = (toss == 0) ? Direction.CW : Direction.CCW;
        piece.setDirection(direction);
        piece.setOriginalDirection(direction);
        publisher.publishCoinToss(piece, direction);
    }

    // Rule T-1 (4.2): increment approachPassCount when CCW piece passes through approach cell
    private void trackApproachPass(Piece piece, int fromPos, int targetPos) {
        int approachCell = board.getApproach(piece.getColour());
        // CCW distance from fromPos to approach cell
        int distFromToApproach = (fromPos - approachCell + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        // CCW steps taken in this move
        int stepsActual = (fromPos - targetPos + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
        if (distFromToApproach > 0 && distFromToApproach <= stepsActual) {
            piece.setApproachPassCount(piece.getApproachPassCount() + 1);
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

    // Rule T-15 (3.3): effects apply only via mystery-cell teleport, not natural landings
    private void handleTeleport(Piece piece, MoveResult result) {
        // For CCW pieces destined for GAMMA → redirect to BETA (spec T-15/Gamma rule)
        TeleportDest effectiveDest = resolveEffectiveDest(piece, result.getTeleportDest());
        applyTeleportDestination(piece, effectiveDest);
        PieceEffect effect = resolveTeleportEffect(effectiveDest);

        if (effect == PieceEffect.DIR_FLIP) {
            Direction from = piece.getDirection();
            Direction to = (from == Direction.CW) ? Direction.CCW : Direction.CW;
            piece.setDirection(to);
            publisher.publishDirectionChange(piece, from, to);
        } else if (effect != PieceEffect.NONE) {
            piece.applyEffect(effect);
            publishEffectEvent(piece, effect);
        }
    }

    // CCW pieces teleported to GAMMA are redirected to BETA instead
    private TeleportDest resolveEffectiveDest(Piece piece, TeleportDest requested) {
        if (requested == TeleportDest.GAMMA && piece.getDirection() == Direction.CCW) {
            publisher.publishGammaCCWTeleportToBeta(piece);
            return TeleportDest.BETA;
        }
        return requested;
    }

    private void publishEffectEvent(Piece piece, PieceEffect effect) {
        switch (effect) {
            case FROZEN    -> publisher.publishPieceFrozen(piece);
            case ENERGISED -> publisher.publishPieceEnergised(piece);
            case SICK      -> publisher.publishPieceSick(piece);
            default        -> {}
        }
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
            case GAMMA:
                movePieceToCell(piece, GameConstants.GAMMA_CELL);
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
            case BETA  -> GameConstants.BETA_CELL;
            case GAMMA -> GameConstants.GAMMA_CELL;
            default    -> GameConstants.NO_POSITION;
        };
    }

    private PieceEffect resolveTeleportEffect(TeleportDest dest) {
        return switch (dest) {
            case ALPHA -> resolveAlphaEffect();
            case BETA  -> PieceEffect.FROZEN;
            case GAMMA -> PieceEffect.DIR_FLIP;
            default    -> PieceEffect.NONE;
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

    // Rule T-13 (3.2): frozen piece teleported to base after rolling three consecutive 3s
    private void handleFrozenEscape(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.getActiveEffect() == PieceEffect.FROZEN) {
                publisher.publishFrozenEscapeToBase(piece);
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

    // Rule T-6 (3.4): detect if player has any block (2+ pieces at same position)
    private boolean playerHasBlock(AbstractPlayer player) {
        Map<Integer, Integer> posCount = new HashMap<>();
        for (Piece piece : player.getPiecesOnBoard()) {
            posCount.merge(piece.getPosition(), 1, Integer::sum);
        }
        for (int count : posCount.values()) {
            if (count >= GameConstants.MIN_BLOCK_SIZE) return true;
        }
        return false;
    }

    // Rule T-6 (3.4): force block break — keep first piece, move rest 6 cells in originalDirection
    private void forceBlockBreak(AbstractPlayer player) {
        Map<Integer, List<Piece>> blockMap = new HashMap<>();
        for (Piece piece : player.getPiecesOnBoard()) {
            blockMap.computeIfAbsent(piece.getPosition(), k -> new ArrayList<>()).add(piece);
        }
        for (Map.Entry<Integer, List<Piece>> entry : blockMap.entrySet()) {
            List<Piece> blockPieces = entry.getValue();
            if (blockPieces.size() >= GameConstants.MIN_BLOCK_SIZE) {
                // Keep the first piece; move all others
                for (int i = 1; i < blockPieces.size(); i++) {
                    Piece piece = blockPieces.get(i);
                    int fromPos = piece.getPosition();
                    int newPos;
                    if (piece.getOriginalDirection() == Direction.CCW) {
                        newPos = (fromPos - GameConstants.TRIPLE_SIX_BLOCKADE_MOVE
                                + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
                    } else {
                        newPos = (fromPos + GameConstants.TRIPLE_SIX_BLOCKADE_MOVE)
                                % GameConstants.BOARD_SIZE;
                    }
                    board.removePiece(piece, fromPos);
                    piece.setPosition(newPos);
                    board.placePiece(piece, newPos);
                    publisher.publishMove(piece, fromPos, newPos, piece.getOriginalDirection());
                }
            }
        }
    }

    public boolean isLudoT() {
        return gameMode == GameMode.LUDO_T;
    }
}
