package shared;

/** Outcome of a request. */
public enum ResponseStatus {

    /** The command ran; {@code payload} holds the result when one was produced. */
    OK,

    /** The command was understood but could not be applied (bad id, wrong state, bad params). */
    ERROR,

    /** The server was too busy to accept the request; the client may retry. */
    REJECTED
}
