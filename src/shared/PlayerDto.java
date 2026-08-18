package shared;

import java.io.Serializable;
import java.util.List;

/** One player and its four pieces, with the counts a status panel needs. */
public record PlayerDto(String colour,
                        String name,
                        String strategy,
                        List<PieceDto> pieces,
                        int piecesOnBoard,
                        int piecesAtBase,
                        int piecesHome,
                        int totalCaptures,
                        boolean finished) implements Serializable {

    private static final long serialVersionUID = 1L;

    public PlayerDto {
        pieces = List.copyOf(pieces);
    }
}
