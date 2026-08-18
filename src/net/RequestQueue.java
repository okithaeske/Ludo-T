package net;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The server's work queue: a bounded {@link ThreadPoolExecutor} in front of an
 * {@link ArrayBlockingQueue}.
 *
 * <h2>Why bounded, and why nothing is ever dropped</h2>
 * Connection threads only decode requests; they never execute them. Every request is handed to
 * this queue and processed by a fixed pool of workers as soon as one is free, so a burst from
 * many clients at once queues rather than spawning a thread per request.
 *
 * <p>The queue has a hard capacity because an unbounded one does not remove the limit, it only
 * hides it — the server would keep accepting work until it exhausted its heap, then fail all
 * at once instead of slowing down. When the queue does fill, the saturation policy runs the
 * task <em>on the calling thread</em> rather than discarding it. Two things follow, both
 * wanted: no client's request is ever silently lost, and the connection thread that submitted
 * it is busy for the duration, so it stops reading that socket and the flooding client is
 * throttled at source. Backpressure, not data loss.
 *
 * <p>{@code saturationCount} records how often that happened. It is the number worth watching
 * during the load demonstration: it shows the queue reaching its limit and the server
 * degrading in an orderly way instead of falling over.
 */
public final class RequestQueue implements AutoCloseable {

    private final ThreadPoolExecutor pool;
    private final BlockingQueue<Runnable> queue;
    private final LongAdder accepted = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder saturation = new LongAdder();
    private final AtomicLong workerSequence = new AtomicLong();
    private final int capacity;

    /**
     * @param workers  number of request-processing threads
     * @param capacity how many requests may wait before the pool applies backpressure
     */
    public RequestQueue(int workers, int capacity) {
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.pool = new ThreadPoolExecutor(
                workers, workers,
                0L, TimeUnit.MILLISECONDS,
                queue,
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "request-worker-" + workerSequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                (task, executor) -> {
                    // Saturation policy: run it here rather than throw it away.
                    saturation.increment();
                    if (!executor.isShutdown()) {
                        task.run();
                    }
                });
    }

    /** Queues one request for processing. */
    public void submit(Runnable task) {
        accepted.increment();
        pool.execute(() -> {
            try {
                task.run();
            } finally {
                completed.increment();
            }
        });
    }

    /** Requests waiting for a worker right now. */
    public int depth() {
        return queue.size();
    }

    public int capacity() {
        return capacity;
    }

    /** Workers currently executing a request. */
    public int activeWorkers() {
        return pool.getActiveCount();
    }

    public int poolSize() {
        return pool.getPoolSize();
    }

    public long acceptedCount() {
        return accepted.sum();
    }

    public long completedCount() {
        return completed.sum();
    }

    /** How many times the queue filled and a submitting thread had to run the work itself. */
    public long saturationCount() {
        return saturation.sum();
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
