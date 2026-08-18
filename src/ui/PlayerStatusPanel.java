package ui;

import shared.BoardSnapshot;
import shared.PieceDto;
import shared.PlayerDto;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Per-player standing: piece counts, captures, strategy, and any active effects. */
public final class PlayerStatusPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final List<PlayerCard> cards = new ArrayList<>();
    private final JPanel body = new JPanel();

    /** One player's row. Rebuilt only when the player set changes, updated in place otherwise. */
    private static final class PlayerCard extends JPanel {

        private static final long serialVersionUID = 1L;

        private final JLabel name = new JLabel();
        private final JLabel counts = new JLabel();
        private final JLabel detail = new JLabel();
        private String colour = "NONE";

        PlayerCard() {
            setLayout(new BorderLayout(6, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setFont(Theme.uiFontBold(12));
            counts.setFont(Theme.monoFont(11));
            detail.setFont(Theme.uiFont(11));
            text.add(name);
            text.add(counts);
            text.add(detail);
            add(text, BorderLayout.CENTER);
        }

        void update(PlayerDto player) {
            colour = player.colour();
            name.setText(player.colour().charAt(0)
                    + player.colour().substring(1).toLowerCase()
                    + (player.finished() ? "  ✓ finished" : ""));
            name.setForeground(Theme.forColour(player.colour()));

            counts.setText(String.format("base %d · board %d · home %d · caps %d",
                    player.piecesAtBase(), player.piecesOnBoard(),
                    player.piecesHome(), player.totalCaptures()));
            counts.setForeground(Theme.text());

            detail.setText(describeEffects(player));
            detail.setForeground(Theme.textMuted());

            setBackground(Theme.forColourSoft(player.colour()));
            setToolTipText(player.strategy());
        }

        /** Summarises effects rather than listing every piece, to keep the card one line. */
        private String describeEffects(PlayerDto player) {
            List<String> notes = new ArrayList<>();
            for (PieceDto piece : player.pieces()) {
                if (!"NONE".equals(piece.effect())) {
                    notes.add(piece.id() + " " + piece.effect().toLowerCase()
                            + " (" + piece.effectRoundsLeft() + ")");
                }
            }
            if (notes.isEmpty()) {
                return player.strategy();
            }
            return String.join(", ", notes);
        }

        void applyTheme() {
            setBackground(Theme.forColourSoft(colour));
            name.setForeground(Theme.forColour(colour));
            counts.setForeground(Theme.text());
            detail.setForeground(Theme.textMuted());
        }
    }

    public PlayerStatusPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(Theme.panel());

        JLabel heading = new JLabel("Players");
        heading.setFont(Theme.uiFontBold(13));
        heading.setForeground(Theme.text());
        add(heading, BorderLayout.NORTH);

        body.setLayout(new GridLayout(4, 1, 0, 6));
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);

        for (int i = 0; i < 4; i++) {
            PlayerCard card = new PlayerCard();
            cards.add(card);
            body.add(card);
        }

        setPreferredSize(new Dimension(250, 240));
    }

    public void showSnapshot(BoardSnapshot snapshot) {
        List<PlayerDto> players = snapshot.players();
        for (int i = 0; i < cards.size(); i++) {
            if (i < players.size()) {
                cards.get(i).setVisible(true);
                cards.get(i).update(players.get(i));
            } else {
                cards.get(i).setVisible(false);
            }
        }
        repaint();
    }

    public void applyTheme() {
        setBackground(Theme.panel());
        cards.forEach(PlayerCard::applyTheme);
        repaint();
    }
}
