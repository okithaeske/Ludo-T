package engine;

import enums.Colour;
import enums.Direction;
import enums.PieceEffect;
import enums.PieceState;
import enums.TeleportDest;
import logger.GameEventPublisher;
import model.Board;
import model.GameConstants;
import model.MoveResult;
import model.Piece;
import model.RandomInitiator;
import player.AbstractPlayer;

import java.util.List;

/** Handles piece effects and mystery-cell teleports. */
public class EffectHandler {

    private final Board board;
    private final TurnManager turnManager;
    private final GameEventPublisher publisher;
    private final RuleEngine ruleEngine;
    private final List<AbstractPlayer> players;

    public EffectHandler(Board board, TurnManager turnManager, GameEventPublisher publisher,
                         RuleEngine ruleEngine, List<AbstractPlayer> players) {
        this.board = board;
        this.turnManager = turnManager;
        this.publisher = publisher;
        this.ruleEngine = ruleEngine;
        this.players = players;
    }

    public boolean hasActiveFrozenPiece(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.isMovementRestricted()) {
                return true;
            }
        }
        return false;
    }

    public boolean isFrozenEscape(AbstractPlayer player) {
        return hasActiveFrozenPiece(player) && turnManager.isTripleThreeForPlayer(player);
    }

    public void tickPlayerEffects(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            piece.tickEffectCountdown();
        }
    }

    public void tickFrozenPiece(AbstractPlayer player) {
        tickPlayerEffects(player);
    }

    public void handleFrozenEscape(AbstractPlayer player) {
        for (Piece piece : player.getPieces()) {
            if (piece.isMovementRestricted()) {
                publisher.publishFrozenEscapeToBase(piece);
                board.removePiece(piece, piece.getPosition());
                piece.reset();
                break;
            }
        }
        turnManager.resetConsecutiveThreesForPlayer(player);
    }

    public void performCoinToss(Piece piece) {
        int toss = RandomInitiator.getInstance().nextInt(2);
        Direction direction = (toss == 0) ? Direction.CW : Direction.CCW;
        piece.assignInitialDirection(direction);
        publisher.publishCoinToss(piece, direction);
    }

    public void handleTeleport(Piece piece, MoveResult result) {
        TeleportDest effectiveDest = resolveEffectiveDest(piece, result.getTeleportDest());
        int targetCell = getTeleportTargetCell(piece, effectiveDest);

        publisher.publishTeleport(piece, effectiveDest, targetCell);

        placeAfterTeleport(piece, effectiveDest, targetCell);

        if (piece.isAtBase()) {
            return;
        }

        applyPostTeleportEffect(piece, effectiveDest);
    }

    private TeleportDest resolveEffectiveDest(Piece piece, TeleportDest requested) {
        if (requested == TeleportDest.GAMMA && piece.getDirection() == Direction.CCW) {
            publisher.publishGammaCCWTeleportToBeta(piece);
            return TeleportDest.BETA;
        }
        return requested;
    }

    private int getTeleportTargetCell(Piece piece, TeleportDest dest) {
        return switch (dest) {
            case BASE     -> GameConstants.NO_POSITION;
            case START_X  -> board.getStartX(piece.getColour());
            case APPROACH -> board.getApproach(piece.getColour());
            case ALPHA    -> GameConstants.ALPHA_CELL;
            case BETA     -> GameConstants.BETA_CELL;
            case GAMMA    -> GameConstants.GAMMA_CELL;
        };
    }

    /** Removes the piece from the board and places it at the teleport destination. */
    private void placeAfterTeleport(Piece piece, TeleportDest dest, int targetCell) {
        board.removePiece(piece, piece.getPosition());
        piece.setHomeStraightPosition(0);

        if (dest == TeleportDest.BASE) {
            piece.reset();
            return;
        }

        // Move the piece to the destination BEFORE resolving conflicts so that any
        // capture message logged inside resolveConflictAtDestination shows the correct
        // square (the teleport destination, not the mystery cell the piece came from).
        piece.moveTo(targetCell);
        piece.setState(PieceState.ACTIVE);

        resolveConflictAtDestination(piece, targetCell);

        if (!piece.isAtBase()) {
            board.placePiece(piece, targetCell);
        }
    }

    /**
     * Handles opponent pieces already occupying the teleport destination.
     * A lone opponent is captured; a full blockade sends the teleporting piece back to base
     * (a single piece cannot displace a blockade — Rule T-11 §2.3).
     */
    private void resolveConflictAtDestination(Piece piece, int targetCell) {
        List<Piece> piecesAtTarget = board.getPiecesAt(targetCell);
        long opponentCount = piecesAtTarget.stream()
                .filter(p -> p.getColour() != piece.getColour())
                .count();

        if (opponentCount == 1) {
            captureOpponentAtDestination(piece, piecesAtTarget);
        } else if (opponentCount >= GameConstants.MIN_BLOCK_SIZE) {
            piece.reset();
        }
    }

    private void captureOpponentAtDestination(Piece piece, List<Piece> piecesAtTarget) {
        Piece captured = piecesAtTarget.stream()
                .filter(p -> p.getColour() != piece.getColour())
                .findFirst()
                .orElse(null);
        if (captured == null) return;
        AbstractPlayer victimPlayer = findPlayerByColour(captured.getColour());
        publisher.publishCapture(piece, captured);
        piece.capture();
        board.removePiece(captured, captured.getPosition());
        captured.reset();
        publisher.publishCapturedPlayerStatus(victimPlayer);
        turnManager.grantExtraRoll();
    }

    private AbstractPlayer findPlayerByColour(Colour colour) {
        for (AbstractPlayer p : players) {
            if (p.getColour() == colour) return p;
        }
        throw new IllegalStateException("No player found for colour " + colour);
    }

    private void applyPostTeleportEffect(Piece piece, TeleportDest dest) {
        PieceEffect effect = resolveTeleportEffect(dest);

        if (effect == PieceEffect.DIR_FLIP) {
            flipDirection(piece);
        } else if (effect != PieceEffect.NONE) {
            piece.applyEffect(effect);
            publishEffectEvent(piece, effect);
        }
    }

    private void flipDirection(Piece piece) {
        Direction from = piece.getDirection();
        Direction to = (from == Direction.CW) ? Direction.CCW : Direction.CW;
        piece.setDirection(to);
        publisher.publishDirectionChange(piece, from, to);
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

    private void publishEffectEvent(Piece piece, PieceEffect effect) {
        switch (effect) {
            case FROZEN    -> publisher.publishPieceFrozen(piece);
            case ENERGISED -> publisher.publishPieceEnergised(piece);
            case SICK      -> publisher.publishPieceSick(piece);
            default        -> { }
        }
    }
}
