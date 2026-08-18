/**
 * The wire contract: the only package present on <em>both</em> the client and server
 * classpaths.
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li>{@link shared.Request} / {@link shared.Response} / {@link shared.Command} &ndash;
 *       the request-reply half of the protocol, correlated by request id so a client may keep
 *       many requests in flight.</li>
 *   <li>{@link shared.ServerEvent} / {@link shared.ServerEventType} &ndash; the push half:
 *       messages the server sends unprompted, which is how one client's action reaches
 *       another client's screen without polling.</li>
 *   <li>{@link shared.BoardSnapshot}, {@link shared.PlayerDto}, {@link shared.PieceDto},
 *       {@link shared.SessionSummaryDto}, {@link shared.ServerMetricsDto} &ndash;
 *       <em>DTO pattern</em>; immutable records carrying data and no behaviour.</li>
 *   <li>{@link shared.ProtocolCodec} &ndash; framing over a socket, used identically by both
 *       peers so the handshake ordering cannot diverge.</li>
 * </ul>
 *
 * <h2>Why this package holds no logic</h2>
 * These types cross a process boundary, so anything they <em>did</em> would have to exist and
 * behave identically in two applications at once. Keeping them inert means the client can only
 * read data the server chose to send: no client holds a reference to a domain {@code Piece} or
 * {@code Board}, and therefore no client can mutate server state by touching one.
 *
 * <h2>Dependency direction</h2>
 * {@code shared} is a frameworks-and-drivers concern. It depends on nothing in this codebase,
 * and — importantly — neither {@code model}, {@code engine}, {@code player} nor {@code app}
 * import it. The mapping from application output models to these DTOs happens in
 * {@code adapter}, which is the only package that knows both vocabularies.
 */
package shared;
