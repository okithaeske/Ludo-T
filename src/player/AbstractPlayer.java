package player;

import enums.Colour;
import model.Board;
import model.GameConstants;
import model.NoPiece;
import model.Piece;
import player.strategy.PieceSelectionStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Template Method</b> — defines the complete structure of a Ludo player: piece
 * ownership, lifecycle queries ({@link #getPiecesOnBoard()}, {@link #getPiecesAtBase()},
 * {@link #allHome()}), and board helpers ({@link #getPieceClosestToHome(model.Board)},
 * {@link #canCaptureOpponent(model.Piece, int, model.Board)}). Concrete subclasses
 * ({@code RedPlayer}, {@code GreenPlayer}, {@code YellowPlayer}, {@code BluePlayer})
 * supply only colour and name to the super-constructor; all behaviour is inherited.
 *
 * <p>Piece <em>selection</em> is handled by the injected
 * {@link player.strategy.PieceSelectionStrategy} (Strategy pattern), keeping player
 * subclasses trivially thin.
 *
 * @see player.strategy.PieceSelectionStrategy
 * @see player.PlayerFactory
 */
public abstract class AbstractPlayer {

    protected Colour colour;
    protected Piece[] pieces;
    protected String name;
    private PieceSelectionStrategy strategy;

    public AbstractPlayer(Colour colour, String name) {
        this.colour = colour;
        this.name = name;
        this.pieces = new Piece[GameConstants.NUM_PIECES];
        initialisePieces();
    }

    public void setStrategy(PieceSelectionStrategy strategy) {
        this.strategy = strategy;
    }

    private void initialisePieces() {
        for (int i = 0; i < GameConstants.NUM_PIECES; i++) {
            pieces[i] = new Piece(colour.name().charAt(0) + String.valueOf(i + 1), colour);
        }
    }

    public Piece choosePiece(int roll, Board board) {
        Piece selected = strategy.choosePiece(roll, board);
        if (selected.isNull() || !selected.isMovementRestricted()) {
            return selected;
        }
        // A frozen piece cannot move; fall back to any movable active piece.
        List<Piece> movable = getMovablePiecesOnBoard();
        if (!movable.isEmpty()) {
            return movable.getFirst();
        }
        return NoPiece.getInstance();
    }

    public List<Piece> getPiecesOnBoard() {
        List<Piece> onBoard = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.getState().isOnBoard()) {
                onBoard.add(piece);
            }
        }
        return onBoard;
    }

    public List<Piece> getMovablePiecesOnBoard() {
        List<Piece> movable = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.getState().isOnBoard() && !piece.isMovementRestricted()) {
                movable.add(piece);
            }
        }
        return movable;
    }

    public List<Piece> getPiecesAtBase() {
        List<Piece> atBase = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.getState().requiresSixToMove()) {
                atBase.add(piece);
            }
        }
        return atBase;
    }

    public boolean allHome() {
        for (Piece piece : pieces) {
            if (!piece.getState().hasFinished()) {
                return false;
            }
        }
        return true;
    }

    public Piece getPieceClosestToHome(Board board) {
        Piece closest = NoPiece.getInstance();
        int minDistance = Integer.MAX_VALUE;
        for (Piece piece : getMovablePiecesOnBoard()) {
            int distance = board.distanceToHome(piece);
            if (distance < minDistance) {
                minDistance = distance;
                closest = piece;
            }
        }
        return closest;
    }

    public boolean hasPiecesAtBase() {
        return !getPiecesAtBase().isEmpty();
    }

    public boolean hasOpponentPieceAt(List<Piece> piecesAtTarget) {
        for (Piece target : piecesAtTarget) {
            if (target.getColour() != this.colour) {
                return true;
            }
        }
        return false;
    }

    public boolean canCaptureOpponent(Piece piece, int roll, Board board) {
        if (piece.isMovementRestricted() || piece.isInHomeStraight()) {
            return false;
        }
        int targetCell = board.projectPosition(piece, piece.getEffectiveRoll(roll));
        if (targetCell == GameConstants.NO_POSITION) {
            return false;
        }
        return hasOpponentPieceAt(board.getPiecesAt(targetCell));
    }

    public Colour getColour() { return colour; }
    public String getName()   { return name; }
    public Piece[] getPieces() { return pieces; }
}
