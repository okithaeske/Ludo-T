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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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
 * <h2>Writer thread and the two outbound queues</h2>
 * Nothing writes to the socket except the writer thread. This is the detail that stops a single
 * slow client from damaging everyone else. Pushes originate on <em>session</em> threads; if
 * those threads wrote to sockets directly, a client that stopped reading would block the game
 * loop that was trying to notify it, and that game would freeze for every viewer. Instead a
 * session drops the event in a queue and returns immediately.
 *
 * <p>There are <em>two</em> such queues, because the two kinds of frame have opposite
 * requirements, and one queue cannot honour both:
 *
 * <ul>
 *   <li><b>Events are droppable.</b> Each snapshot supersedes the last, so a client that has
 *       fallen behind is better served by the current board than by a backlog of stale ones.
 *       When {@link #events} fills, the <em>oldest</em> pending event is sacrificed.</li>
 *   <li><b>Responses are not.</b> A response is the other half of a request somebody is
 *       waiting on; discarding one leaves its caller waiting forever, with nothing to retry.
 *       {@link #responses} is therefore never evicted. When it fills, the request worker
 *       offering the frame <em>blocks</em> — that is backpressure, the same argument already
 *       made for the inbound {@link RequestQueue}.</li>
 * </ul>
 *
 * <p>The writer always drains {@link #responses} first, so a flood of pushes can delay a
 * response but can no longer displace one. {@link #signal} counts frames waiting across both
 * queues, which is what lets the writer park without polling and still wake for whichever
 * queue received the frame.
 *
 * <p>This replaced a single shared drop-oldest queue, under which the step-6 load clients lost
 * 348 of 8,000 responses — evicted by pushes from the games they were subscribed to. See
 * {@code docs/DESIGN-RATIONALE.md} §2.1.
 */
public final class ClientConnection implements ClientSession, Runnable {

    /** Pending events per client before the oldest is sacrificed. */
    private static final int EVENT_CAPACITY = 512;

    /** Unwritten responses per client before a request worker is made to wait. */
    private static final int RESPONSE_CAPACITY = 256;

    /**
     * How long a request worker waits for room in {@link #responses} before concluding that the
     * peer is gone rather than merely slow. 256 unread responses <em>and</em> no drain for this
     * long is not a slow client; it is a dead one, and the connection is closed.
     */
    private static final long RESPONSE_STALL_MILLIS = 10_000L;

    private final String id;
    private final Socket socket;
    private final ProtocolCodec codec;
    private final RequestRouter router;
    private final RequestQueue requestQueue;
    private final ConnectionRegistry registry;

    /** Never evicted: a lost response is a caller waiting forever. Drained first. */
    private final BlockingQueue<Serializable> responses =
            new ArrayBlockingQueue<>(RESPONSE_CAPACITY);

    /** Evicted oldest-first when full: the newest board supersedes every one behind it. */
    private final BlockingQueue<Serializable> events =
            new ArrayBlockingQueue<>(EVENT_CAPACITY);

    /**
     * One permit per frame waiting in either queue. The writer acquires before draining, so it
     * parks when idle instead of polling, yet wakes for whichever queue was written to.
     */
    private final Semaphore signal = new Semaphore(0);

    /**
     * Held only by event producers, only across an evict-then-offer pair. The writer never takes
     * it — it only ever removes frames, which can only create room — so the offer after a
     * successful eviction cannot fail, and the queue's size (and therefore {@link #signal}'s
     * permit count) is unchanged by a drop-and-replace.
     */
    private final ReentrantLock eventLock = new ReentrantLock();
    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final LongAdder droppedEvents = new LongAdder();

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
        enqueueResponse(response);
    }

    // ── Writer thread ────────────────────────────────────────────────────────

    private void writeLoop() {
        try {
            while (open.get()) {
                signal.acquire();
                // Responses first, always: a push flood may delay an answer, never displace it.
                Serializable frame = responses.poll();
                if (frame == null) {
                    frame = events.poll();
                }
                if (frame == null) {
                    continue;   // close() releases a permit purely to wake this thread.
                }
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
     * Queues an event for the writer. Never blocks the caller — sessions publish from their own
     * threads while owning a game, and must not wait on a socket to finish a round.
     */
    private void enqueueEvent(Serializable frame) {
        if (!open.get()) {
            return;
        }
        boolean added;
        eventLock.lock();
        try {
            added = events.offer(frame);
            if (!added && events.poll() != null) {
                // Full: sacrifice the oldest. A client this far behind wants the current board,
                // not a queue of superseded ones. Size is unchanged, so no new permit is owed —
                // the evicted frame's permit is still outstanding and now stands for this one.
                droppedEvents.increment();
                events.offer(frame);
            }
        } finally {
            eventLock.unlock();
        }
        if (added) {
            signal.release();
        }
    }

    /**
     * Queues a response for the writer. Unlike an event, this frame is never dropped: its caller
     * is blocked on the answer and has nothing to retry. If the queue is full the request worker
     * waits, which throttles that one client at its source without touching any other.
     */
    private void enqueueResponse(Serializable frame) {
        if (!open.get()) {
            return;
        }
        try {
            if (responses.offer(frame, RESPONSE_STALL_MILLIS, TimeUnit.MILLISECONDS)) {
                signal.release();
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        // Waited the full stall budget for a client that is not draining. Losing its answers is
        // now unavoidable; say so and tear the connection down rather than park workers on it.
        System.out.println("[server] connection " + id + " is not reading responses; closing");
        close();
    }

    /** Pushes a server event if this client is watching the game it concerns. */
    void push(ServerEvent event) {
        enqueueEvent(event);
    }

    /** Stamps and pushes an event, assigning this connection's next sequence number. */
    void pushWithSequence(java.util.function.LongFunction<ServerEvent> eventFactory) {
        enqueueEvent(eventFactory.apply(eventSequence.incrementAndGet()));
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

    /** Events sacrificed to keep this client's queue bounded. Responses are never counted here
     *  because responses are never dropped. */
    public long droppedCount() {
        return droppedEvents.sum();
    }

    public boolean isOpen() {
        return open.get();
    }

    /** Idempotent: safe to call from the reader, the writer, or the server shutting down. */
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        signal.release();       // wake the writer if it is parked on an empty queue
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
