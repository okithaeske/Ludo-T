package net;

import adapter.ClientSession;
import adapter.RequestRouter;
import shared.ProtocolCodec;
import shared.Request;
import shared.Response;
import shared.ServerEvent;

import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * One connected client, served by exactly two threads.
 *
 * <h2>Reader thread</h2>
 * Decodes frames and hands each request to the shared {@link RequestQueue}. It does no game
 * work itself: if it executed requests inline, one client's slow command would stop that
 * client's other requests from even being read, and the "process requests as soon as possible"
 * property would be lost.
 *
 * <h2>Writer thread and the outbound queue</h2>
 * Nothing writes to the socket except the writer thread, draining a bounded queue. This is the
 * detail that stops a single slow client from damaging everyone else. Pushes originate on
 * <em>session</em> threads; if those threads wrote to sockets directly, a client that stopped
 * reading would block the game loop that was trying to notify it, and that game would freeze
 * for every viewer. Instead a session drops the event in this queue and returns immediately.
 *
 * <p>The queue is bounded, so a client that never reads cannot consume the server's heap. When
 * it fills, the <em>oldest</em> pending event is discarded to make room. Discarding old state
 * is the right trade here: each snapshot supersedes the last, so a client that has fallen
 * behind is better served by the current board than by a backlog of stale ones. Responses to
 * requests are queued the same way but are only ever dropped under the same extreme pressure,
 * and {@link #droppedCount()} records every loss rather than hiding it.
 */
public final class ClientConnection implements ClientSession, Runnable {

    /** Pending outbound frames per client before the oldest is sacrificed. */
    private static final int OUTBOUND_CAPACITY = 512;

    private final String id;
    private final Socket socket;
    private final ProtocolCodec codec;
    private final RequestRouter router;
    private final RequestQueue requestQueue;
    private final ConnectionRegistry registry;

    private final BlockingQueue<Serializable> outbound =
            new ArrayBlockingQueue<>(OUTBOUND_CAPACITY);
    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final LongAdder dropped = new LongAdder();

    private final Thread writerThread;

    ClientConnection(String id, Socket socket, ProtocolCodec codec, RequestRouter router,
                     RequestQueue requestQueue, ConnectionRegistry registry) {
        this.id = id;
        this.socket = socket;
        this.codec = codec;
        this.router = router;
        this.requestQueue = requestQueue;
        this.registry = registry;
        this.writerThread = new Thread(this::writeLoop, "client-writer-" + id);
        this.writerThread.setDaemon(true);
    }

    /** Starts the writer thread. The reader runs on whichever thread calls {@link #run()}. */
    void startWriter() {
        writerThread.start();
    }

    // ── Reader thread ────────────────────────────────────────────────────────

    @Override
    public void run() {
        try {
            while (open.get()) {
                Request request = codec.read(Request.class);
                // Queue it; do not execute it here. See the class note.
                requestQueue.submit(() -> handle(request));
            }
        } catch (EOFException e) {
            // Client closed cleanly; nothing to report.
        } catch (IOException e) {
            if (open.get()) {
                System.out.println("[server] connection " + id + " ended: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /** Runs on a request worker, never on the reader or a session thread. */
    private void handle(Request request) {
        Response response = router.route(request, this);
        enqueue(response);
    }

    // ── Writer thread ────────────────────────────────────────────────────────

    private void writeLoop() {
        try {
            while (open.get()) {
                Serializable frame = outbound.take();
                codec.write(frame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Peer went away mid-write; the reader will notice and tear the connection down.
        } finally {
            close();
        }
    }

    /**
     * Queues a frame for the writer thread. Never blocks the caller — sessions publish from
     * their own threads and must not wait on a socket.
     */
    private void enqueue(Serializable frame) {
        if (!open.get()) {
            return;
        }
        while (!outbound.offer(frame)) {
            // Full: sacrifice the oldest pending frame and try again. A client this far behind
            // wants the current board, not a queue of superseded ones.
            if (outbound.poll() != null) {
                dropped.increment();
            }
        }
    }

    /** Pushes a server event if this client is watching the game it concerns. */
    void push(ServerEvent event) {
        enqueue(event);
    }

    /** Stamps and pushes an event, assigning this connection's next sequence number. */
    void pushWithSequence(java.util.function.LongFunction<ServerEvent> eventFactory) {
        enqueue(eventFactory.apply(eventSequence.incrementAndGet()));
    }

    // ── ClientSession ────────────────────────────────────────────────────────

    @Override
    public String id() {
        return id;
    }

    @Override
    public void subscribe(String gameId) {
        subscriptions.add(gameId);
    }

    @Override
    public void unsubscribe(String gameId) {
        subscriptions.remove(gameId);
    }

    @Override
    public boolean isSubscribed(String gameId) {
        return subscriptions.contains(gameId);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public long droppedCount() {
        return dropped.sum();
    }

    public boolean isOpen() {
        return open.get();
    }

    /** Idempotent: safe to call from the reader, the writer, or the server shutting down. */
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        writerThread.interrupt();
        codec.close();
        // Give back the viewer counts this client was holding before dropping the connection.
        registry.releaseSubscriptions(subscriptions);
        subscriptions.clear();
        registry.remove(id);
    }

    Socket getSocket() {
        return socket;
    }
}
