package app.model;

import java.util.List;

/** Application-layer output model for one player and its pieces. */
public record PlayerView(String colour,
                         String name,
                         String strategy,
                         List<PieceView> pieces,
                         int piecesOnBoard,
                         int piecesAtBase,
                         int piecesHome,
                         int totalCaptures,
                         boolean finished) {

    public PlayerView {
        pieces = List.copyOf(pieces);
    }
}
