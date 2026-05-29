package logger;

import enums.Direction;
import enums.PieceState;
import model.Block;
import model.MysteryCell;
import model.Piece;
import player.AbstractPlayer;

import java.util.List;

public class Logger implements GameEventListener {

    @Override
    public void onGameStart() {
        System.out.println("Game started");
    }

    @Override
    public void onRoll(AbstractPlayer player, int value) {
        System.out.println(player.getName() + " rolled " + value);
    }

    @Override
    public void onMove(Piece piece, int from, int to, Direction direction) {
        String toStr = piece.isInHomeStraight()
                ? piece.getColour().name().toLowerCase() + "homepath" + (piece.getHomeStraightPosition() - 1)
                : String.valueOf(to);
        System.out.println(piece.getId() + " moved from " + from + " to " + toStr + " direction " + direction);
    }

    @Override
    public void onCapture(Piece attacker, Piece victim) {
        System.out.println(attacker.getId() + " captured " + victim.getId());
    }

    @Override
    public void onWin(AbstractPlayer player, int place) {
        String[] ordinals = {"1st", "2nd", "3rd", "4th"};
        String ordinal = (place >= 1 && place <= 4) ? ordinals[place - 1] : place + "th";
        System.out.println(player.getName() + " finishes in " + ordinal + " place!");
    }

    @Override
    public void onRoundComplete() {
        System.out.println("Round complete");
    }

    @Override
    public void onMysterySpawn(int position) {
        System.out.println("Mystery cell spawned at " + position);
    }

    @Override
    public void onBlockFormed(Block block) {
        System.out.println("Block formed at " + block.getPosition() + " size " + block.getSize());
    }

    @Override
    public void onCoinToss(Piece piece, Direction direction) {
        String dirName = direction == Direction.CW ? "clockwise" : "counterclockwise";
        System.out.println(piece.getColour() + " piece " + piece.getId()
                + " direction determined as " + dirName + " by coin toss.");
    }

    @Override
    public void onDirectionChange(Piece piece, Direction from, Direction to) {
        String fromName = from == Direction.CW ? "clockwise" : "counterclockwise";
        String toName   = to   == Direction.CW ? "clockwise" : "counterclockwise";
        System.out.println("The " + piece.getColour() + " piece " + piece.getId()
                + ", which was moving " + fromName
                + ", has changed to moving " + toName + ".");
    }

    @Override
    public void onGammaCCWTeleportToBeta(Piece piece) {
        System.out.println("The " + piece.getColour() + " piece " + piece.getId()
                + " is moving in a counterclockwise direction. Teleporting to Beta from Gamma.");
    }

    @Override
    public void onPieceFrozen(Piece piece) {
        System.out.println(piece.getColour() + " piece " + piece.getId()
                + " attends briefing and cannot move for four rounds.");
    }

    @Override
    public void onFrozenEscapeToBase(Piece piece) {
        System.out.println(piece.getColour() + " piece " + piece.getId()
                + " is movement-restricted and has rolled three consecutively."
                + " Teleporting piece " + piece.getId() + " to base.");
    }

    @Override
    public void onPieceEnergised(Piece piece) {
        System.out.println(piece.getColour() + " piece " + piece.getId()
                + " feels energized, and movement speed doubles.");
    }

    @Override
    public void onPieceSick(Piece piece) {
        System.out.println(piece.getColour() + " piece " + piece.getId()
                + " feels sick, and movement speed halves.");
    }

    @Override
    public void onPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) {
        System.out.println(piece.getColour()
                + " does not have other pieces in the board to move instead of the blocked piece."
                + " Moved the piece to square " + movedTo
                + " which is the cell before the block.");
    }

    @Override
    public void onNoValidMove(AbstractPlayer player) {
        System.out.println(player.getColour()
                + " does not have other pieces in the board to move instead of the blocked piece."
                + " Ignoring the throw and moving on to the next player.");
    }

    @Override
    public void onRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell) {
        System.out.println("=== Round Summary ===");
        for (AbstractPlayer player : players) {
            int onBoard = player.getPiecesOnBoard().size();
            int atBase  = player.getPiecesAtBase().size();
            System.out.println(player.getColour() + ": " + onBoard + " on board, " + atBase + " at base");
            for (Piece piece : player.getPieces()) {
                String location;
                if (piece.getState() == PieceState.HOME) {
                    location = "Home";
                } else if (piece.getState() == PieceState.BASE) {
                    location = "Base";
                } else if (piece.isInHomeStraight()) {
                    location = piece.getColour().name().toLowerCase()
                            + "homepath" + (piece.getHomeStraightPosition() - 1);
                } else {
                    location = "Cell " + piece.getPosition();
                }
                System.out.println("  " + piece.getId() + ": " + location);
            }
        }
        if (mysteryCell != null && mysteryCell.isActive()) {
            System.out.println("Mystery Cell: Active at cell " + mysteryCell.getPosition()
                    + ", " + mysteryCell.getRoundsRemaining() + " rounds remaining");
        } else {
            System.out.println("Mystery Cell: Inactive");
        }
        System.out.println("====================");
    }

    @Override
    public void onGameInitialisation(List<AbstractPlayer> players) {
        String[] numberWords = {"zero", "one", "two", "three", "four"};
        for (AbstractPlayer player : players) {
            Piece[] pieces = player.getPieces();
            int count = pieces.length;
            String word = count < numberWords.length ? numberWords[count] : String.valueOf(count);
            String formatted = String.format("%02d", count);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0 && i == pieces.length - 1) names.append(", and ");
                else if (i > 0) names.append(", ");
                names.append(pieces[i].getId());
            }
            System.out.println("The " + player.getColour().name().toLowerCase()
                    + " player has " + word + " (" + formatted + ") pieces named " + names + ".");
        }
    }

    @Override
    public void onFirstPlayerSelected(AbstractPlayer player, List<AbstractPlayer> players, List<Integer> rolls) {
        System.out.println(player.getColour().name().toLowerCase()
                + " player has the highest roll and will begin the game.");
        System.out.print("Turn order: ");
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(players.get(i).getColour().name().toLowerCase());
        }
        System.out.println();
    }

    @Override
    public void onPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) {
        System.out.println(piece.getColour() + " player moves piece " + piece.getId()
                + " to the starting point.");
        System.out.println(piece.getColour() + " player now has " + piecesOnBoard
                + "/4 pieces on the board and " + piecesAtBase + "/4 pieces on the base.");
    }

    @Override
    public void onBlockBrokenAtApproach(Block block, int approachCell) {
        System.out.println("Block broken at approach cell " + approachCell
                + ". Pieces placed at approach cell and effects cleared.");
    }

    @Override
    public void onBlockCapture(Block attacker, Block defender) {
        System.out.println("Block capture: attacker block (size " + attacker.getSize()
                + ") captured defender block (size " + defender.getSize() + ").");
    }

    @Override
    public void onGameResult(List<AbstractPlayer> finishingOrder) {
        String[] ordinals = {"1st", "2nd", "3rd", "4th"};
        System.out.println("=== Final Standings ===");
        for (int i = 0; i < finishingOrder.size(); i++) {
            AbstractPlayer player = finishingOrder.get(i);
            String place = (i < ordinals.length) ? ordinals[i] : (i + 1) + "th";
            System.out.println(place + ": " + player.getName());
        }
        System.out.println("=======================");
    }
}
