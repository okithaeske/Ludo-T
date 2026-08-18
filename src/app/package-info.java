/**
 * <b>Use-case layer.</b> What the application <em>does</em>, expressed without reference to how
 * it is delivered.
 *
 * <h2>Rule of this package</h2>
 * Nothing here imports {@code net}, {@code ui}, {@code persistence}, {@code server},
 * {@code client} or {@code shared}. It depends inward on {@code engine}, {@code model},
 * {@code player} and {@code logger}, and outward on nothing at all. Everything it needs from
 * the outside world it declares for itself as a port in {@link app.port} and receives by
 * construction.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@link app.GameSession} &ndash; one hosted game, run as an actor on its own thread.
 *       This is where the server's concurrency model actually lives.</li>
 *   <li>{@link app.SessionRegistry} &ndash; every live game, and the factory that builds
 *       them.</li>
 *   <li>{@link app.SnapshotFactory} &ndash; photographs a running engine into an immutable
 *       {@link app.model.GameSnapshot}.</li>
 *   <li>{@link app.usecase} &ndash; one interactor per use case: create, start, pause, resume,
 *       step, abort, set speed, list, snapshot.</li>
 *   <li>{@link app.model} &ndash; output models: immutable results the interactors return.</li>
 *   <li>{@link app.port} &ndash; the interfaces outer layers must satisfy.</li>
 * </ul>
 *
 * <h2>Why the output models are not the wire DTOs</h2>
 * {@link app.model.GameSnapshot} and {@code shared.BoardSnapshot} carry nearly the same fields,
 * and merging them would delete a mapper. It would also make this layer depend on the wire
 * format, so every protocol change — a renamed field, a new version — would reach in and force
 * the interactors to be recompiled and retested. Keeping them apart is the standard use-case
 * boundary: interactors return output models, and {@code adapter} converts those into whatever
 * the delivery mechanism happens to need.
 *
 * <h2>A note on {@code java.util.concurrent}</h2>
 * This layer uses executors and futures, which are standard-library concurrency primitives
 * rather than a framework. That is a deliberate line: an executor is a tool for structuring
 * work, and no code here knows whether that work arrived from a socket, a GUI button or a
 * test.
 */
package app;
