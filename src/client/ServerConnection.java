package client;

import shared.Command;
import shared.ProtocolCodec;
import shared.Request;
import shared.Response;
import shared.ServerEvent;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The client's whole view of the server: asynchronous request-reply plus a stream of pushes.
 *
 * <h2>Three threads, and why the GUI never freezes</h2>
 * <ul>
 *   <li><b>Swing's EDT</b> paints and handles input. It never reads or writes a socket. Every
 *       method here returns immediately when called from it.</li>
 *   <li><b>A sender thread</b> owns writes. {@link #send} hands the frame over and returns, so
 *       a click cannot block the interface even if the network stalls mid-write — the socket
 *       buffer filling would otherwise freeze the EDT with the button still depressed.</li>
 *   <li><b>A receiver thread</b> owns reads, and blocks on them permanently. That is exactly
 *       what makes pushes appear the moment they arrive rather than when the UI next asks for
 *       something: there is no polling anywhere in this client.</li>
 * </ul>
 *
 * <h2>Correlation</h2>
 * Replies can arrive in any order, so each request carries an id and parks a
 * {@link CompletableFuture} in {@link #pending}. The receiver completes the right one by id.
 * That is what allows the UI to have many requests in flight without ever waiting for one.
 *
 * <h2>Handing work back to the EDT</h2>
 * Listeners registered here are invoked on the EDT via
 * {@link SwingUtilities#invokeLater}. Doing it in this class rather than in each panel means a
 * UI component physically cannot touch a Swing model from the receiver thread, which is the
 * classic way Swing applications corrupt themselves.
 */
public final class ServerConnection implements AutoCloseable {

    private final String host;
    private final int port;
    private final ProtocolCodec codec;
    private final Map<Long, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
    private final AtomicLong requestIds = new AtomicLong();
    private final ExecutorService sender;
    private final Thread receiver;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private final CopyOnWriteArrayList<Consumer<ServerEvent>> eventListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> disconnectListeners =
            new CopyOnWriteArrayList<>();

    private ServerConnection(String host, int port, ProtocolCodec codec) {
        this.host = host;
        this.port = port;
        this.codec = codec;
        this.sender = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "client-sender");
            thread.setDaemon(true);
            return thread;
        });
        this.receiver = new Thread(this::receiveLoop, "client-receiver");
        this.receiver.setDaemon(true);
        this.receiver.start();
    }

    /** Opens a connection. Call off the EDT — this blocks until the handshake completes. */
    public static ServerConnection connect(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        return new ServerConnection(host, port, ProtocolCodec.open(socket));
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isOpen() {
        return open.get();
    }

    /** Registers a listener for server pushes. Invoked on the EDT. */
    public void addEventListener(Consumer<ServerEvent> listener) {
        eventListeners.add(listener);
    }

    /** Registers a listener for connection loss. Invoked on the EDT. */
    public void addDisconnectListener(Runnable listener) {
        disconnectListeners.add(listener);
    }

    /**
     * Sends a request. Returns immediately; the future completes on the receiver thread.
     *
     * <p>Chain UI work with {@code thenAcceptAsync(action, SwingUtilities::invokeLater)} — a
     * plain {@code thenAccept} would run on the receiver thread and touch Swing off the EDT.
     */
    public CompletableFuture<Response> send(Command command, Map<String, String> params) {
        long id = requestIds.incrementAndGet();
        CompletableFuture<Response> future = new CompletableFuture<>();

        if (!open.get()) {
            future.completeExceptionally(new IOException("Not connected"));
            return future;
        }

        pending.put(id, future);
        sender.execute(() -> {
            try {
                codec.write(new Request(id, command, params));
            } catch (IOException e) {
                pending.remove(id);
                future.completeExceptionally(e);
                handleDisconnect();
            }
        });
        return future;
    }

    public CompletableFuture<Response> send(Command command) {
        return send(command, Map.of());
    }

    /** Convenience for the many commands whose only parameter is a game id. */
    public CompletableFuture<Response> sendForGame(Command command, String gameId) {
        return send(command, Map.of("gameId", gameId));
    }

    private void receiveLoop() {
        try {
            while (open.get()) {
                Object frame = codec.read();
                if (frame instanceof Response response) {
                    CompletableFuture<Response> waiting = pending.remove(response.getRequestId());
                    if (waiting != null) {
                        waiting.complete(response);
                    }
                } else if (frame instanceof ServerEvent event) {
                    dispatch(event);
                }
            }
        } catch (IOException e) {
            // Expected on shutdown, and the signal for a genuine drop.
        } finally {
            handleDisconnect();
        }
    }

    private void dispatch(ServerEvent event) {
        // Cross to the EDT here so no listener can accidentally touch Swing off-thread.
        SwingUtilities.invokeLater(() -> {
            for (Consumer<ServerEvent> listener : eventListeners) {
                listener.accept(event);
            }
        });
    }

    private void handleDisconnect() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        // Fail everything still waiting, or the UI would show spinners that never resolve.
        IOException cause = new IOException("Connection to the server was lost");
        pending.values().forEach(future -> future.completeExceptionally(cause));
        pending.clear();

        SwingUtilities.invokeLater(() -> disconnectListeners.forEach(Runnable::run));
    }

    @Override
    public void close() {
        open.set(false);
        codec.close();
        sender.shutdownNow();
        receiver.interrupt();
    }
}
