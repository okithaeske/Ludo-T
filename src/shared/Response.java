package shared;

import java.io.Serializable;

/** Reply to exactly one {@link Request}, correlated by {@link #getRequestId()}. */
public final class Response implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long requestId;
    private final ResponseStatus status;
    private final String message;
    private final Serializable payload;

    public Response(long requestId, ResponseStatus status, String message, Serializable payload) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    public static Response ok(long requestId, Serializable payload) {
        return new Response(requestId, ResponseStatus.OK, "OK", payload);
    }

    public static Response ok(long requestId, String message) {
        return new Response(requestId, ResponseStatus.OK, message, null);
    }

    public static Response error(long requestId, String message) {
        return new Response(requestId, ResponseStatus.ERROR, message, null);
    }

    public static Response rejected(long requestId, String message) {
        return new Response(requestId, ResponseStatus.REJECTED, message, null);
    }

    public long getRequestId() {
        return requestId;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Serializable getPayload() {
        return payload;
    }

    public boolean isOk() {
        return status == ResponseStatus.OK;
    }

    @Override
    public String toString() {
        return "Response#" + requestId + " " + status + " " + message
                + (payload == null ? "" : " " + payload);
    }
}
