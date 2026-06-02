package test;

import enums.Colour;
import enums.PieceState;
import model.Board;
import model.GameConstants;
import model.MysteryCell;
import model.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MysteryCell")
class MysteryCellTest extends BaseTest {

    private Board board;
    private MysteryCell mysteryCell;

    @BeforeEach
    void setUp() {
        board = new Board();
        mysteryCell = new MysteryCell(GameConstants.NO_POSITION);
    }

    // spawn()
    @Test
    @DisplayName("should_notSpawn_when_noPiecesAreOnStandardPath")
    void should_notSpawn_when_noPiecesAreOnStandardPath() {
        // Arrange Ã¢â‚¬â€ board is empty

        // Act
        boolean spawned = mysteryCell.spawn(board);

        // Assert
        Assertions.assertFalse(spawned);
        Assertions.assertFalse(mysteryCell.isActive());
    }

    @Test
    @DisplayName("should_spawn_when_pieceIsOnStandardPath")
    void should_spawn_when_pieceIsOnStandardPath() {
        // Arrange
        Piece dummy = new Piece("R1", Colour.RED);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(5);
        board.placePiece(dummy, 5);

        // Act
        boolean spawned = mysteryCell.spawn(board);

        // Assert
        Assertions.assertTrue(spawned);
        Assertions.assertTrue(mysteryCell.isActive());
    }

    @Test
    @DisplayName("should_neverSpawnOnOccupiedCell_when_spawning")
    void should_neverSpawnOnOccupiedCell_when_spawning() {
        // Arrange Ã¢â‚¬â€ fill cells 0-10 with pieces so mystery must avoid them
        Piece[] pieces = new Piece[11];
        for (int i = 0; i <= 10; i++) {
            pieces[i] = new Piece("P" + i, Colour.RED);
            pieces[i].setState(PieceState.ACTIVE);
            pieces[i].moveTo(i);
            board.placePiece(pieces[i], i);
        }

        // Act
        mysteryCell.spawn(board);

        // Assert
        int spawnedAt = mysteryCell.getPosition();
        Assertions.assertTrue(spawnedAt < 0 || !board.isOccupied(spawnedAt),
                "Mystery cell spawned on occupied cell " + spawnedAt);
    }

    // tick()
    @Test
    @DisplayName("should_decrementRoundsRemaining_when_ticked")
    void should_decrementRoundsRemaining_when_ticked() {
        // Arrange
        Piece dummy = new Piece("R1", Colour.RED);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(5);
        board.placePiece(dummy, 5);
        mysteryCell.spawn(board);
        int before = mysteryCell.getRoundsRemaining();

        // Act
        mysteryCell.tick(board);

        // Assert
        Assertions.assertEquals(before - 1, mysteryCell.getRoundsRemaining());
    }

    @Test
    @DisplayName("should_relocate_when_roundsRemainingReachesZero")
    void should_relocate_when_roundsRemainingReachesZero() {
        // Arrange
        Piece dummy = new Piece("R1", Colour.RED);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(5);
        board.placePiece(dummy, 5);
        mysteryCell.spawn(board);

        // Act Ã¢â‚¬â€ tick down to zero
        for (int i = 0; i < GameConstants.MYSTERY_CELL_DURATION; i++) {
            mysteryCell.tick(board);
        }

        // Assert  mystery cell either relocated (active) or no empty cell found (inactive);
        // either way, roundsRemaining was reset or mystery deactivated Ã¢â‚¬â€ not still counting down.
        Assertions.assertTrue(mysteryCell.getRoundsRemaining() == GameConstants.MYSTERY_CELL_DURATION
                   || !mysteryCell.isActive());
    }

    // relocate()
    @Test
    @DisplayName("should_neverReturnToLastPosition_when_relocating")
    void should_neverReturnToLastPosition_when_relocating() {
        // Arrange
        Piece dummy = new Piece("R1", Colour.RED);
        dummy.setState(PieceState.ACTIVE);
        dummy.moveTo(5);
        board.placePiece(dummy, 5);

        mysteryCell.spawn(board);
        int firstPosition = mysteryCell.getPosition();

        // Act
        mysteryCell.relocate(board);

        // Assert
        int secondPosition = mysteryCell.getPosition();
        // If the mystery is still active it must have moved to a different cell
        if (mysteryCell.isActive()) {
            Assertions.assertNotEquals(firstPosition, secondPosition);
        }
    }
}

