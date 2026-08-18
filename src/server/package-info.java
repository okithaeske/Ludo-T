/**
 * <b>The server application</b> — one of the three processes in the deployed system, alongside
 * the client and the database.
 *
 * <ul>
 *   <li>{@link server.ServerAssembly} &ndash; the composition root; the single place where
 *       dependencies are created and wired.</li>
 *   <li>{@link server.ServerMain} &ndash; the {@code main} method: read config, start, broadcast
 *       metrics, wait for Ctrl+C.</li>
 *   <li>{@link server.ServerConfig} &ndash; command-line tuning.</li>
 * </ul>
 *
 * <p>Keeping construction in one class is what lets every other class receive its
 * collaborators rather than reach for them, and it means the integration tests exercise the
 * same assembly that runs in the demonstration rather than a parallel one that could drift.
 */
package server;
