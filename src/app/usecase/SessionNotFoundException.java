package app.usecase;

/** Thrown when a command names a session the server is not hosting. */
public class SessionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionNotFoundException(String gameId) {
        super("No such game: " + gameId);
    }
}
