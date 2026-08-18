/**
 * <b>Frameworks and drivers: the network.</b> Sockets, threads, and the request queue.
 *
 * <h2>Threading model</h2>
 * <table border="1">
 *   <caption>Threads and what each one is forbidden to do</caption>
 *   <tr><th>Thread</th><th>Responsibility</th><th>Must never</th></tr>
 *   <tr><td>accept ({@link net.LudoServer})</td><td>complete handshakes, register
 *       connections</td><td>do any per-request work</td></tr>
 *   <tr><td>client-reader ({@link net.ClientConnection})</td><td>decode frames, enqueue
 *       requests</td><td>execute a request</td></tr>
 *   <tr><td>request-worker ({@link net.RequestQueue})</td><td>route and execute
 *       requests</td><td>touch a game engine directly</td></tr>
 *   <tr><td>game actor ({@code app.GameSession})</td><td>all engine mutation</td><td>write to
 *       a socket</td></tr>
 *   <tr><td>client-writer ({@link net.ClientConnection})</td><td>drain the outbound queue to
 *       the socket</td><td>block anyone else</td></tr>
 * </table>
 *
 * <h2>The two queues, and why both are bounded</h2>
 * {@link net.RequestQueue} bounds inbound work: when it fills, the submitting thread runs the
 * task itself, which throttles the flooding client at source and loses nothing.
 * {@link net.ClientConnection}'s outbound queue bounds per-client backlog: when it fills, the
 * oldest pending frame is discarded, because a client that has fallen behind wants the current
 * board rather than a queue of superseded ones. Neither queue is unbounded, since an unbounded
 * queue does not remove a limit — it converts a slowdown into an out-of-memory failure.
 *
 * <h2>Dependency direction</h2>
 * This layer implements interfaces declared further in — {@code app.port.EventSink},
 * {@code adapter.ClientSession}, {@code adapter.MetricsProvider} — and nothing further in ever
 * imports {@code net}. That inversion is what allows every layer beneath to be tested with no
 * socket in sight.
 */
package net;
