package client.testing;

import shared.Command;
import shared.ProtocolCodec;
import shared.Request;
import shared.Response;
import shared.ResponseStatus;
import shared.ServerEvent;
import shared.ServerEventType;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One headless connection: asynchronous request-reply plus the push stream, with a stopwatch.
 *
 * <h2>Why this is not {@link client.ServerConnection}</h2>
 * The two have deliberately the same threading shape — a sender thread that owns writes, a
 * receiver thread that owns reads and blocks permanently, and a map of futures correlated by
 * request id. They differ in one place that cannot be shared: {@code ServerConnection} hands
 * every listener to {@code SwingUtilities.invokeLater}, which is the single most important
 * thing it does and completely wrong here. Marshalling a load test's responses onto the event
 * dispatch thread would funnel every client through one thread and measure Swing rather than
 * the server.
 *
 * <p>The alternative — extracting a shared base class and injecting the dispatch strategy —
 * was rejected: it would put a seam through working GUI code to serve a test harness, and the
 * duplicated part is thirty lines of correlation logic whose behaviour is already covered by
 * {@code ProtocolCodecTest}. The GUI client stays the thing the tutor sees running, untouched.
 *
 * <h2>What the timer measures</h2>
 * The clock starts when {@link #send} is called and stops when the receiver completes the
 * future, so a sample includes client-side sender-queue time, both network hops, the server's
 * inbound queue wait and the actual work. That is deliberately the whole round trip as a
 * caller experiences it — measuring only the server's own handling would report a number no
 * user could ever observe, and would hide exactly the queueing this harness exists to show.
 */
public final class AsyncConnection implements AutoCloseable {

    /** Notified on the receiver thread as each answer lands. Must not block. */
    @FunctionalInterface
    public interface ResponseObserver {
        void onAnswer(Command command, ResponseStatus status, long latencyNanos);
    }

    /** Notified for a request that will never be answered. */
    @FunctionalInterface
    public interface FailureObserver {
        void onFailure(Command command);
    }

    private record InFlight(Command command, long sentNanos, CompletableFuture<Response> future) {
    }

    private final String name;
    private final ProtocolCodec codec;
    private final Map<Long, InFlight> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestIds = new AtomicLong();
    private final ExecutorService sender;
    private final Thread receiver;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private final AtomicLong eventsReceived = new AtomicLong();
    private final AtomicLong snapshotsReceived = new AtomicLong();

    private volatile ResponseObserver responseObserver = (command, status, nanos) -> { };
    private volatile FailureObserver failureObserver = command -> { };

    private AsyncConnection(String name, ProtocolCodec codec) {
        this.name = name;
        this.codec = codec;
        this.sender = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name + "-sender");
            thread.setDaemon(true);
            return thread;
        });
        this.receiver = new Thread(this::receiveLoop, name + "-receiver");
        this.receiver.setDaemon(true);
        this.receiver.start();
    }

    /** Opens a connection. Blocks for the handshake, so never call it from a worker thread. */
    public static AsyncConnection connect(String name, String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        // Nagle would batch small frames together and forge the latency figures.
        socket.setTcpNoDelay(true);
        return new AsyncConnection(name, ProtocolCodec.open(socket));
    }

    public void setResponseObserver(ResponseObserver observer) {
        this.responseObserver = observer;
    }

    public void setFailureObserver(FailureObserver observer) {
        this.failureObserver = observer;
    }

    public String name() {
        return name;
    }

    public boolean isOpen() {
        return open.get();
    }

    public long eventsReceived() {
        return eventsReceived.get();
    }

    public long snapshotsReceived() {
        return snapshotsReceived.get();
    }

    public int inFlightCount() {
        return pending.size();
    }

    /** Sends without waiting. The future completes on the receiver thread. */
    public CompletableFuture<Response> send(Command command, Map<String, String> params) {
        long id = requestIds.incrementAndGet();
        CompletableFuture<Response> future = new CompletableFuture<>();

        if (!open.get()) {
            failureObserver.onFailure(command);
            future.completeExceptionally(new IOException("Not connected"));
            return future;
        }

        pending.put(id, new InFlight(command, System.nanoTime(), future));
        try {
            sender.execute(() -> writeFrame(new Request(id, command, params)));
        } catch (RuntimeException e) {
            // The sender was shut down between the open() check and the submit.
            pending.remove(id);
            failureObserver.onFailure(command);
            future.completeExceptionally(new IOException("Client is shutting down", e));
        }
        return future;
    }

    public CompletableFuture<Response> send(Command command) {
        return send(command, Map.of());
    }

    /** Blocking convenience for setup and teardown only — never used on the load path. */
    public Response call(Command command, Map<String, String> params, long timeoutMillis)
            throws Exception {
        return send(command, params).get(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void writeFrame(Request request) {
        try {
            codec.write(request);
        } catch (IOException e) {
            InFlight sent = pending.remove(request.getId());
            if (sent != null) {
                failureObserver.onFailure(sent.command());
                sent.future().completeExceptionally(e);
            }
            handleDisconnect();
        }
    }

    private void receiveLoop() {
        try {
            while (open.get()) {
                Object frame = codec.read();
                if (frame instanceof Response response) {
                    completeResponse(response);
                } else if (frame instanceof ServerEvent event) {
                    countEvent(event);
                }
            }
        } catch (IOException e) {
            // Expected at shutdown; a genuine drop is reported through handleDisconnect.
        } finally {
            handleDisconnect();
        }
    }

    private void completeResponse(Response response) {
        InFlight sent = pending.remove(response.getRequestId());
        if (sent == null) {
            return; // A late answer to something already abandoned.
        }
        responseObserver.onAnswer(sent.command(), response.getStatus(),
                System.nanoTime() - sent.sentNanos());
        sent.future().complete(response);
    }

    private void countEvent(ServerEvent event) {
        eventsReceived.incrementAndGet();
        if (event.getType() == ServerEventType.SNAPSHOT
                || event.getType() == ServerEventType.GAME_OVER) {
            snapshotsReceived.incrementAndGet();
        }
    }

    private void handleDisconnect() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        // Fail everything outstanding, or a worker waiting on a permit never gets it back and
        // the harness hangs at the end of the run instead of reporting the drop.
        IOException cause = new IOException("Connection to the server was lost");
        pending.forEach((id, sent) -> {
            failureObserver.onFailure(sent.command());
            sent.future().completeExceptionally(cause);
        });
        pending.clear();
    }

    @Override
    public void close() {
        handleDisconnect();
        codec.close();
        sender.shutdownNow();
        receiver.interrupt();
    }
}
