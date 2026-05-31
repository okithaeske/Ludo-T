package enums;

public enum GameMode {
    CLASSIC,
    LUDO_T;

    /** Eliminates repeated {@code mode == GameMode.LUDO_T} checks across engine classes. */
    public boolean isLudoT() {
        return this == LUDO_T;
    }
}
