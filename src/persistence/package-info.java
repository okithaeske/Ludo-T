/**
 * The database tier's client: JDBC behind the {@code app.port.GameRepository} interface.
 *
 * <h2>Where this sits in the dependency rule</h2>
 * This is an outermost layer, exactly like {@code net} and {@code ui}. It depends inward on
 * {@code app.port} and {@code app.model}; nothing in {@code app}, {@code engine} or
 * {@code model} depends on it, and none of them mentions SQL, JDBC or H2. Deleting this
 * package and passing {@code GameRepository.NO_OP} leaves a server that still compiles, still
 * runs and still plays games — which is the practical test of whether the inversion is real.
 *
 * <p>The port is declared in {@code app} and implemented here, so the arrow between the
 * application and the database points <em>from</em> the detail <em>to</em> the policy, against
 * the direction of control flow. That is the Dependency Inversion Principle applied across a
 * process boundary rather than a package one.
 *
 * <h2>Why this tier is allowed to lose data and the others are not</h2>
 * See {@code JdbcGameRepository}: writes are queued and dropped under pressure rather than
 * made to block a game's round loop. The choice follows from what is stored. A live game is
 * never in here — it lives in its actor's memory — so nothing this tier loses can affect a
 * simulation, a client's view of one, or an answer to a command.
 */
package persistence;
