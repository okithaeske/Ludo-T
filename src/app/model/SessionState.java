package app.model;

/**
 * Lifecycle of one hosted game.
 *
 * <pre>
 *   CREATED ──start──▶ RUNNING ──pause──▶ PAUSED
 *      │                  │ ▲               │
 *      │                  │ └────resume─────┘
 *      │                  ▼
 *      │              FINISHED
 *      └──────────abort──────────▶ ABORTED
 * </pre>
 *
 * <p>Defined here rather than reused from {@code shared} on purpose: the application layer
 * must not depend on the wire format. {@code adapter} maps this to its DTO spelling.
 */
public enum SessionState {

    CREATED,
    RUNNING,
    PAUSED,
    FINISHED,
    ABORTED;

    /** True once the session can never run again, so callers can stop polling it. */
    public boolean isTerminal() {
        return this == FINISHED || this == ABORTED;
    }

    /** True when a single round may be advanced by hand. */
    public boolean canStep() {
        return this == CREATED || this == PAUSED;
    }
}
