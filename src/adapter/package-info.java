/**
 * <b>Interface adapters.</b> The translation layer between the application's vocabulary and
 * the outside world's.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@link adapter.GameEventBroadcaster} &ndash; a second implementation of the domain's
 *       existing {@code logger.GameEventListener} port, registered exactly where {@code Logger}
 *       used to sit. The engine publishes events as it always did and cannot tell they now
 *       leave the process.</li>
 *   <li>{@link adapter.RequestRouter} &ndash; table-driven dispatch from a
 *       {@code shared.Command} to a controller.</li>
 *   <li>{@link adapter.GameController}, {@link adapter.LobbyController} &ndash; parse and
 *       validate request parameters, call interactors, shape responses. No game rules
 *       whatsoever.</li>
 *   <li>{@link adapter.SnapshotMapper}, {@link adapter.ServerEventMapper} &ndash; application
 *       output models to wire DTOs.</li>
 *   <li>{@link adapter.ClientSession}, {@link adapter.MetricsProvider} &ndash; interfaces this
 *       layer declares and the network layer implements, so controllers can subscribe a client
 *       or read server load without ever seeing a socket.</li>
 * </ul>
 *
 * <h2>Position in the dependency graph</h2>
 * This is the only layer that knows both {@code app.model} and {@code shared}. That is its
 * entire purpose: the knowledge of how two representations correspond has to live somewhere,
 * and concentrating it here keeps it out of both the application layer and the protocol.
 *
 * <h2>Controllers hold no rules</h2>
 * Everything here is translation and validation. If a decision about the game would change
 * were the client a GUI rather than a socket, it belongs here; if it would not, it belongs in
 * {@code app} or deeper. That test is what keeps the layer thin.
 */
package adapter;
