package adapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who may control each game: the client that created it, and nobody else.
 *
 * <p>Every client can still <em>watch</em> every game — listing, subscribing and reading a
 * snapshot are never checked here. Only the commands that change a game are, so one player
 * cannot pause or abort a game another player is running.
 *
 * <h2>Why a key rather than the connection</h2>
 * The owner is identified by a random key the client makes up and sends with
 * {@code CREATE_GAME}, not by the socket the request arrived on. A connection is the wrong
 * identity: it dies on every network drop, so a client that reconnected would find itself
 * locked out of its own games. The key is never sent to any other client — it is not part of
 * any DTO — so knowing a game's id is not enough to control it.
 *
 * <p>A game created without a key has no owner and stays open to everyone. That is what the
 * console client and the load harness do, and it is the behaviour they were measured under.
 *
 * <h2>Threading</h2>
 * Written by whichever request worker handles a create and read by every other, hence the
 * {@link ConcurrentHashMap}. An entry is written once and never changed, so there is no
 * check-then-act to protect.
 */
public final class GameOwnership {

    /** The request parameter carrying the client's key. */
    public static final String PARAM_CONTROL_KEY = "controlKey";

    private final Map<String, String> ownerKeys = new ConcurrentHashMap<>();

    /** Records {@code key} as the owner of a newly created game. A blank key claims nothing. */
    public void claim(String gameId, String key) {
        if (key != null && !key.isBlank()) {
            ownerKeys.putIfAbsent(gameId, key);
        }
    }

    /** True when {@code key} may change the game: it is the owner's, or the game has no owner. */
    public boolean permits(String gameId, String key) {
        String owner = ownerKeys.get(gameId);
        return owner == null || owner.equals(key);
    }
}
