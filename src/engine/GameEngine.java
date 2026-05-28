package engine;

import enums.Direction;
import enums.GameMode;
import enums.PieceEffect;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventPublisher;
import logger.Logger;
import model.Block;
import model.BlockMoveResult;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.MysteryCell;
import model.Piece;
import model.RandomInitiator;
import player.AbstractPlayer;
import player.PlayerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameEngine {

    private final Board board;
    private final TurnManager turnManager;
    private final RuleEngine ruleEngine;
    private final List<AbstractPlayer> players;
    private final GameEventPublisher publisher;
    private int roundNumber;
    private boolean gameOver;
    private final GameMode gameMode;
    private final List<AbstractPlayer> finishingOrder = new ArrayList<>();


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
        // approachPassCount is already 0 in Piece constructor — nothing to do
    }

    public void determineFirstPlayer() {
        AbstractPlayer firstPlayer = rollForFirstPlayer();
        turnManager.setOrder(firstPlayer);
    }

    // Fix 10: re-roll tied players until one has a strictly higher roll
    private AbstractPlayer rollForFirstPlayer() {
        Map<AbstractPlayer, Integer> rollResults = new LinkedHashMap<>();
        List<Integer> rolls = new ArrayList<>();

        for (AbstractPlayer player : players) {
            int roll = turnManager.rollDice();
            rolls.add(roll);
            rollResults.put(player, roll);
            publisher.publishRoll(player, roll);
        }

        int highestRoll = Collections.max(rollResults.values());
        List<AbstractPlayer> tied = rollResults.entrySet().stream()
                .filter(e -> e.getValue() == highestRoll)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        while (tied.size() > 1) {
            Map<AbstractPlayer, Integer> tieBreak = new LinkedHashMap<>();
            for (AbstractPlayer player : tied) {
                int roll = turnManager.rollDice();
                tieBreak.put(player, roll);
                publisher.publishRoll(player, roll);
            }
            int maxTie = Collections.max(tieBreak.values());
            tied = tieBreak.entrySet().stream()
                    .filter(e -> e.getValue() == maxTie)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        AbstractPlayer firstPlayer = tied.get(0);
        publisher.publishFirstPlayerSelected(firstPlayer, players, rolls);
        return firstPlayer;
    }

    public void runGameLoop() {
        while (isGameNotOver()) {
            executeRound();
        }
    }

    private boolean isGameNotOver() {
        return !gameOver;
    }

    // Fix 22/23: skip finished players, check win per turn, stop round when game over
    // Fix 11: extra roll loop per player
    public void executeRound() {
        roundNumber++;
        handleMysteryCell();

        for (AbstractPlayer player : players) {
            if (finishingOrder.contains(player)) continue;
            executeTurn(player, false);
            while (turnManager.isExtraRollPending()) {
                turnManager.clearExtraRoll();
                executeTurn(player, true);
            }
            turnManager.advanceToNextPlayer();
            if (checkWinCondition(player)) return;
        }

        publisher.publishRoundSummary(players, board.getMysteryCell());
        publisher.publishRoundComplete();
    }

    private void handleMysteryCell() {
        if (isLudoT() && isMysterySpawnRound()) {
            spawnOrRelocateMysteryCell();
        }
    }

    private boolean isMysterySpawnRound() {
        return roundNumber >= GameConstants.MYSTERY_SPAWN_ROUND;
    }

    // Fix 9: pass board to tick() so relocation avoids occupied cells
    private void spawnOrRelocateMysteryCell() {
        if (board.getMysteryCell() == null) {
            board.setMysteryCell(new MysteryCell(GameConstants.NO_POSITION));
            board.getMysteryCell().spawn(board);
        } else {
            board.getMysteryCell().tick(board);
        }
        publisher.publishMysterySpawn(board.getMysteryPosition());
    }

    // Fix 17: isExtraRoll controls whether frozen tick fires
    private void executeTurn(AbstractPlayer player, boolean isExtraRoll) {
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

        // Fix 8: per-player frozen escape check
        if (isFrozenEscape(player)) {
            handleFrozenEscape(player);
            return;
        }

        // Fix 17: only decrement frozen counter on the player's first roll, not extra rolls
        if (hasActiveFrozenPiece(player)) {
            if (!isExtraRoll) {
                tickFrozenPiece(player);
            }
            return;
        }

        Piece chosenPiece = player.choosePiece(roll, board);
        if (chosenPiece.isNull()) {
            publisher.publishNoValidMove(player);
            return;
        }

        Block currentBlock = buildBlockForPiece(chosenPiece);
        MoveResult result;
        if (currentBlock != null) {
            result = ruleEngine.validateBlockMove(currentBlock, roll);
        } else {
            result = ruleEngine.validateMove(chosenPiece, roll);
        }

        // Fix 6: only grant bonus roll when move actually succeeded
        if (result.isValid()) {
            applyMove(chosenPiece, result, player, roll, currentBlock);
            handleExtraRoll(roll);
        }
    }

    private Block buildBlockForPiece(Piece piece) {
        // Pieces at the approach cell must enter the home straight individually
        if (piece.getPosition() == board.getApproach(piece.getColour())) return null;

        List<Piece> sameColorAtPos = new ArrayList<>();
        for (Piece p : board.getPiecesAt(piece.getPosition())) {
            if (p.getColour() == piece.getColour()) {
                sameColorAtPos.add(p);
            }
        }
        if (sameColorAtPos.size() < GameConstants.MIN_BLOCK_SIZE) return null;
        Block block = new Block(piece.getPosition(), piece.getDirection());
        for (Piece p : sameColorAtPos) {
            block.addPiece(p);
        }
        return block;
    }

    private void applyMove(Piece piece, MoveResult result, AbstractPlayer player, int roll, Block currentBlock) {
        int fromCell = piece.getPosition();

        // Home straight moves: don't update standard board position
        if (result.isEnteringHomeStraight() || result.isMovingInHomeStraight()) {
            piece.setHomeStraightPosition(result.getHomeStraightPosition());
            piece.clearMovementEffects();
            publisher.publishMove(piece, fromCell, result.getHomeStraightPosition(), piece.getDirection());
            return;
        }

        if (currentBlock != null) {
            if (result.isBlockedAtAdjacent()) {
                // Block stopped at adjacent cell — move all pieces to adjacent
                int targetCell = result.getTargetCell();
                for (Piece bp : new ArrayList<>(currentBlock.getPieces())) {
                    board.removePiece(bp, bp.getPosition());
                    bp.setPosition(targetCell);
                    board.placePiece(bp, targetCell);
                }
                // Fix 15: guard setState — blocks don't teleport to BASE but guard for safety
                if (result.getTeleportDest() != TeleportDest.BASE) {
                    piece.setState(PieceState.ACTIVE);
                }
                publisher.publishMove(piece, fromCell, targetCell, piece.getDirection());
                // Fix 18: block cell is direction-dependent
                int blockedAt = (piece.getDirection() == Direction.CCW)
                        ? (targetCell - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE
                        : targetCell + 1;
                publisher.publishPieceBlockedAtAdjacent(piece, blockedAt, targetCell);
            } else {
                // Normal block move or block capture — resolveBlock handles all piece positions
                BlockMoveResult blockResult = ruleEngine.resolveBlock(currentBlock, roll);
                if (result.getTeleportDest() != TeleportDest.BASE) {
                    piece.setState(PieceState.ACTIVE);
                }
                if (blockResult.brokeAtApproach()) {
                    publisher.publishBlockBrokenAtApproach(currentBlock, blockResult.getApproachCell());
                    return;
                }
                publisher.publishMove(piece, fromCell, piece.getPosition(), piece.getDirection());
            }
        } else {
            // Normal single-piece board move
            board.removePiece(piece, piece.getPosition());
            piece.setPosition(result.getTargetCell());
            board.placePiece(piece, result.getTargetCell());

            // Fix 15: only set ACTIVE if piece is not about to be teleported to BASE
            if (result.getTeleportDest() != TeleportDest.BASE) {
                piece.setState(PieceState.ACTIVE);
            }

            // Rule T-1 (3.1): coin toss when piece moves from base to X
            if (fromCell == GameConstants.BASE_POSITION) {
                performCoinToss(piece);
                publisher.publishPieceMoveToX(piece,
                        player.getPiecesOnBoard().size(),
                        player.getPiecesAtBase().size());
            } else {
                publisher.publishMove(piece, fromCell, result.getTargetCell(), piece.getDirection());
            }
        }

        // Rule T-1 (4.2): track CCW approach cell passes for home entry
        if (isLudoT() && piece.getDirection() == Direction.CCW
                && !result.isHome() && fromCell != GameConstants.BASE_POSITION) {
            trackApproachPass(piece, fromCell, piece.getPosition());
        }

        // Fix 18: blocked-at-adjacent log for single non-block pieces (direction-aware)
        if (result.isBlockedAtAdjacent() && currentBlock == null) {
            int blockedAt = (piece.getDirection() == Direction.CCW)
                    ? (result.getTargetCell() - 1 + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE
                    : result.getTargetCell() + 1;
            publisher.publishPieceBlockedAtAdjacent(piece, blockedAt, result.getTargetCell());
        }

        // Capture handling
        if (result.isBlockCapture() && currentBlock != null) {
            ruleEngine.applyBlockCapture(currentBlock, result.getDefenderBlock());
            publisher.publishBlockCapture(currentBlock, result.getDefenderBlock());
        } else if (result.isCapture() && currentBlock != null) {
            // Block captures a single piece — all block pieces get capture count
            for (Piece blockPiece : currentBlock.getPieces()) {
                blockPiece.capture();
            }
            board.removePiece(result.getCapturedPiece(), result.getCapturedPiece().getPosition());
            result.getCapturedPiece().reset();
            turnManager.grantExtraRoll();
        } else if (result.isCapture()) {
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
        int distFromToApproach = (fromPos - approachCell + GameConstants.BOARD_SIZE) % GameConstants.BOARD_SIZE;
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

    // Fix 22/23: track finishing order; stop when FINISHING_PLAYERS_TO_END have finished
    public boolean checkWinCondition(AbstractPlayer justMoved) {
        if (justMoved.allHome() && !finishingOrder.contains(justMoved)) {
            finishingOrder.add(justMoved);
            publisher.publishWin(justMoved, finishingOrder.size());
            if (finishingOrder.size() >= GameConstants.FINISHING_PLAYERS_TO_END) {
                // Add the remaining player as last place
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

    private boolean hasActiveFrozenPiece(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.getActiveEffect() == PieceEffect.FROZEN
                    && piece.getEffectRoundsLeft() > 0) {
                return true;
            }
        }
        return false;
    }

    // Fix 8: use per-player triple-three check
    private boolean isFrozenEscape(AbstractPlayer player) {
        return hasActiveFrozenPiece(player) && turnManager.isTripleThreeForPlayer(player);
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
        turnManager.resetConsecutiveThreesForPlayer(player);
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
