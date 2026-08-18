package shared;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A client request. Immutable once sent.
 *
 * <p>{@code id} is chosen by the client and echoed back in the matching {@link Response}, so a
 * client may have many requests in flight at once and still pair each reply with its caller.
 * This is what lets the client be fully asynchronous rather than request-response-blocking.
 */
public final class Request implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final Command command;
    private final Map<String, String> params;

    public Request(long id, Command command, Map<String, String> params) {
        this.id = id;
        this.command = command;
        this.params = Map.copyOf(params);
    }

    public static Request of(long id, Command command) {
        return new Request(id, command, Map.of());
    }

    public static Request of(long id, Command command, String key, String value) {
        return new Request(id, command, Map.of(key, value));
    }

    /** Returns a copy of this request with one extra parameter. */
    public Request with(String key, String value) {
        Map<String, String> merged = new HashMap<>(params);
        merged.put(key, value);
        return new Request(id, command, merged);
    }

    public long getId() {
        return id;
    }

    public Command getCommand() {
        return command;
    }

    public Map<String, String> getParams() {
        return params;
    }

    /** Returns the named parameter, or {@code null} when absent. */
    public String param(String key) {
        return params.get(key);
    }

    /** Returns the named parameter, or {@code fallback} when absent or blank. */
    public String paramOrDefault(String key, String fallback) {
        String value = params.get(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    @Override
    public String toString() {
        return "Request#" + id + " " + command + " " + params;
    }
}
