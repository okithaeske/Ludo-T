package logger;

import enums.Direction;
import enums.TeleportDest;
import model.GameConstants;
import model.MysteryCell;
import model.Piece;
import model.Block;
import player.AbstractPlayer;

import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Spec-compliant console logger backed by {@link java.util.logging.Logger}
 *
 * <p>A custom {@link Formatter} strips the default JUL timestamp/class header so
 * each message appears on its own line exactly as the spec requires.
 */
public class Logger implements GameEventListener {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Logger.class.getName());

    static {
        LOG.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return logRecord.getMessage() + System.lineSeparator();
            }
        });
        LOG.addHandler(handler);
    }

    private static String colour(Piece piece) {
        return piece.getColour().name().toLowerCase();
    }

    private static String colour(AbstractPlayer player) {
        return player.getColour().name().toLowerCase();
    }

    private static String direction(Direction d) {
        return d == Direction.CW ? "clockwise" : "counterclockwise";
    }

    private static String destName(TeleportDest dest) {
        return switch (dest) {
            case ALPHA    -> "Alpha";
            case BETA     -> "Beta";
            case GAMMA    -> "Gamma";
            case BASE     -> "Base";
            case START_X  -> "X";
            case APPROACH -> "Approach";
        };
    }

    private static String unit(int steps) {
        return steps == 1 ? "unit" : "units";
    }

    private static void log(String message) {
        LOG.info(message);
    }

    @Override
    public void onGameStart() {
        log("Game started");
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
            log("The " + colour(player)
                    + " player has " + word + " (" + formatted + ") pieces named " + names + ".");
        }
    }

    @Override
    public void onSelectionRoll(AbstractPlayer player, int value) {
        log(colour(player) + " rolls " + value);
    }

    @Override
    public void onRoll(AbstractPlayer player, int value) {
        log(colour(player) + " player rolled " + value + ".");
    }

    @Override
    public void onFirstPlayerSelected(AbstractPlayer player, List<AbstractPlayer> players,
                                       List<Integer> rolls) {
        log(colour(player) + " player has the highest roll and will begin the game.");

        StringBuilder order = new StringBuilder("The order of a single round is ");
        for (int i = 0; i < players.size(); i++) {
            if (i > 0 && i == players.size() - 1) order.append(", and ");
            else if (i > 0) order.append(", ");
            order.append(colour(players.get(i)));
        }
        order.append(".");
        log(order.toString());
    }

    @Override
    public void onPieceMoveToX(Piece piece, int piecesOnBoard, int piecesAtBase) {
        log(colour(piece) + " player moves piece " + piece.getId() + " to the starting point.");
        log(colour(piece) + " player now has " + piecesOnBoard
                + "/4 on pieces on the board and " + piecesAtBase + "/4 pieces on the base.");
    }

    @Override
    public void onMove(Piece piece, int from, int to, Direction direction, int steps) {
        String fromLabel;
        String toLabel;

        if (to == GameConstants.BOARD_SIZE) {
            int oldHomePos = GameConstants.HOME_EXIT_DISTANCE - steps;
            fromLabel = (oldHomePos > 0)
                    ? colour(piece) + "homepath" + (oldHomePos - 1)
                    : String.valueOf(from);
            log(colour(piece) + " moves piece " + piece.getId()
                    + " from location " + fromLabel + " to Home.");
            return;
        }

        if (piece.isInHomeStraight()) {
            int newPos = piece.getHomeStraightPosition();
            int oldPos = newPos - steps;
            toLabel   = colour(piece) + "homepath" + (newPos - 1);
            fromLabel = (oldPos > 0)
                    ? colour(piece) + "homepath" + (oldPos - 1)
                    : String.valueOf(from);
        } else {
            fromLabel = String.valueOf(from);
            toLabel   = String.valueOf(to);
        }

        log(colour(piece) + " moves piece " + piece.getId()
                + " from location " + fromLabel + " to " + toLabel
                + " by " + steps + " " + unit(steps) + " in " + direction(direction) + " direction.");
    }

    @Override
    public void onPieceBlocked(Piece piece, int from, int blockedAt, List<Piece> blockers) {
        String blockerDesc = blockers.isEmpty() ? "a block"
                : colour(blockers.get(0)) + " piece " + blockers.get(0).getId();
        log(colour(piece) + " piece " + piece.getId()
                + " is blocked from moving from " + from + " to " + blockedAt
                + " by " + blockerDesc + ".");
    }

    @Override
    public void onPieceBlockedAtAdjacent(Piece piece, int blockedAt, int movedTo) {
        log(colour(piece)
                + " does not have other pieces in the board to move instead of the blocked piece."
                + " Moved the piece to square " + movedTo
                + " which is the cell before the block.");
    }

    @Override
    public void onNoValidMove(AbstractPlayer player) {
        log(colour(player)
                + " does not have other pieces in the board to move instead of the blocked piece."
                + " Ignoring the throw and moving on to the next player.");
    }

    @Override
    public void onCapture(Piece attacker, Piece victim) {
        log(colour(attacker) + " piece " + attacker.getId()
                + " lands on square " + attacker.getPosition()
                + ", captures " + colour(victim) + " piece " + victim.getId()
                + ", and returns it to the base.");
    }

    @Override
    public void onCapturedPlayerStatus(AbstractPlayer player) {
        log(colour(player) + " player now has " + player.getPiecesOnBoard().size()
                + "/4 on pieces on the board and " + player.getPiecesAtBase().size()
                + "/4 pieces on the base.");
    }

    @Override
    public void onMysterySpawn(int position) {
        log("A mystery cell has spawned in location " + position
                + " and will be at this location for the next four rounds.");
    }

    @Override
    public void onTeleport(Piece piece, TeleportDest dest, int targetCell) {
        String name = destName(dest);
        log(colour(piece) + " player lands on a mystery cell and is teleported to " + name + ".");
        log(colour(piece) + " piece " + piece.getId() + " teleported to " + name + ".");
    }

    @Override
    public void onCoinToss(Piece piece, Direction direction) {
        log(colour(piece) + " piece " + piece.getId()
                + " direction determined as " + direction(direction) + " by coin toss.");
    }

    @Override
    public void onDirectionChange(Piece piece, Direction from, Direction to) {
        log("The " + colour(piece) + " piece " + piece.getId()
                + ", which was moving " + direction(from)
                + ", has changed to moving " + direction(to) + ".");
    }

    @Override
    public void onGammaCCWTeleportToBeta(Piece piece) {
        log("The " + colour(piece) + " piece " + piece.getId()
                + " is moving in a counterclockwise direction. Teleporting to Beta from Gamma.");
    }

    @Override
    public void onPieceFrozen(Piece piece) {
        log(colour(piece) + " piece " + piece.getId()
                + " attends briefing and cannot move for four rounds.");
    }

    @Override
    public void onFrozenEscapeToBase(Piece piece) {
        log(colour(piece) + " piece " + piece.getId()
                + " is movement-restricted and has rolled three consecutively."
                + " Teleporting piece " + piece.getId() + " to base.");
    }

    @Override
    public void onPieceEnergised(Piece piece) {
        log(colour(piece) + " piece " + piece.getId()
                + " feels energized, and movement speed doubles.");
    }

    @Override
    public void onPieceSick(Piece piece) {
        log(colour(piece) + " piece " + piece.getId()
                + " feels sick, and movement speed halves.");
    }

    @Override
    public void onWin(AbstractPlayer player, int place) {
        log(colour(player) + " player wins!!!");
    }

    @Override
    public void onRoundComplete() {
        log("Round complete");
    }

    @Override
    public void onRoundSummary(List<AbstractPlayer> players, MysteryCell mysteryCell) {
        String sep = "============================";
        for (AbstractPlayer player : players) {
            int onBoard = player.getPiecesOnBoard().size();
            int atBase  = player.getPiecesAtBase().size();
            log(colour(player) + " player now has " + onBoard
                    + "/4 on pieces on the board and " + atBase + "/4 pieces on the base.");
            log(sep);
            log("Location of pieces " + colour(player));
            log(sep);
            for (Piece piece : player.getPieces()) {
                String location;
                if (piece.getState().hasFinished()) {
                    location = "Home";
                } else if (piece.getState().requiresSixToMove()) {
                    location = "Base";
                } else if (piece.isInHomeStraight()) {
                    location = colour(piece) + "homepath" + (piece.getHomeStraightPosition() - 1);
                } else {
                    location = String.valueOf(piece.getPosition());
                }
                log("Piece " + piece.getId() + " -> " + location);
            }
        }
        if (mysteryCell != null && mysteryCell.isActive()) {
            log("The mystery cell is at " + mysteryCell.getPosition()
                    + " and will be at that location for the next "
                    + mysteryCell.getRoundsRemaining() + " values.");
        }
    }

    @Override
    public void onBlockFormed(Block block) {
        log("Block formed at " + block.getPosition() + " size " + block.getSize());
    }

    @Override
    public void onBlockBrokenAtApproach(Block block, int approachCell) {
        log("Block broken at approach cell " + approachCell
                + ". Pieces placed at approach cell and effects cleared.");
    }

    @Override
    public void onBlockCapture(Block attacker, Block defender) {
        log("Block capture: attacker block (size " + attacker.getSize()
                + ") captured defender block (size " + defender.getSize() + ").");
    }

    @Override
    public void onGameResult(List<AbstractPlayer> finishingOrder) {
        String[] ordinals = {"1st", "2nd", "3rd", "4th"};
        log("=== Final Standings ===");
        for (int i = 0; i < finishingOrder.size(); i++) {
            String place = (i < ordinals.length) ? ordinals[i] : (i + 1) + "th";
            log(place + ": " + finishingOrder.get(i).getName());
        }
        log("=======================");
    }
}
