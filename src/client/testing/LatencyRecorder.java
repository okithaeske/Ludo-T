package client.testing;

import shared.ResponseStatus;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects response latencies from every load thread at once and reports percentiles.
 *
 * <h2>Why samples rather than a running average</h2>
 * A mean hides exactly the thing this harness exists to find. Under a bounded queue with a
 * caller-runs saturation policy the median barely moves while the tail explodes, because the
 * throttling lands on whichever connection is flooding. Only p99 shows that, so individual
 * samples have to be kept.
 *
 * <h2>Why a fixed array rather than a growing list</h2>
 * The recorder is written by every worker thread on the response path, so it is on the hot
 * path of the very thing being measured. A pre-allocated {@code long[]} with an
 * {@link AtomicInteger} cursor costs one CAS per sample and never allocates, never resizes and
 * never triggers a copy mid-run — a growing list would add GC pressure and resize pauses to
 * the measurements it is supposed to be taking. Samples beyond the capacity are counted in
 * {@link #overflowCount()} rather than silently discarded: a report that quietly stopped
 * sampling would understate the tail, which is the one number worth having.
 *
 * <p>Counts are kept separately from samples because a rejected or failed request has no
 * meaningful latency but must still appear in the report — a run that "got fast" by failing
 * early is the classic way a load test lies.
 */
public final class LatencyRecorder {

    /** ~1.6 MB of longs. Large enough that a demo run never overflows it. */
    public static final int DEFAULT_CAPACITY = 200_000;

    private final String name;
    private final long[] samples;
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicLong overflow = new AtomicLong();
    private final AtomicLong ok = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public LatencyRecorder(String name) {
        this(name, DEFAULT_CAPACITY);
    }

    public LatencyRecorder(String name, int capacity) {
        this.name = name;
        this.samples = new long[capacity];
    }

    /** Records one answered request. Safe to call from any number of threads. */
    public void record(ResponseStatus status, long latencyNanos) {
        switch (status) {
            case OK -> ok.incrementAndGet();
            case REJECTED -> rejected.incrementAndGet();
            case ERROR -> errors.incrementAndGet();
        }
        int slot = cursor.getAndIncrement();
        if (slot < samples.length) {
            samples[slot] = latencyNanos;
        } else {
            overflow.incrementAndGet();
        }
    }

    /**
     * Records a request that never received an answer — a dropped connection or a client-side
     * write failure. Counted, never timed: a timeout is not a latency.
     */
    public void recordFailure() {
        failures.incrementAndGet();
    }

    public String name() {
        return name;
    }

    public long overflowCount() {
        return overflow.get();
    }

    public long answeredCount() {
        return ok.get() + rejected.get() + errors.get();
    }

    /** True when nothing was recorded, so the report can skip the row entirely. */
    public boolean isEmpty() {
        return answeredCount() == 0 && failures.get() == 0;
    }

    /**
     * Takes a consistent-enough view of the run. Called after the load phase has drained, so
     * no writer is still active; taking it mid-run would read a partially written tail.
     */
    public Stats stats() {
        int recorded = Math.min(cursor.get(), samples.length);
        long[] sorted = Arrays.copyOf(samples, recorded);
        Arrays.sort(sorted);
        return new Stats(name, ok.get(), rejected.get(), errors.get(), failures.get(),
                percentile(sorted, 50), percentile(sorted, 90), percentile(sorted, 99),
                sorted.length == 0 ? 0L : sorted[sorted.length - 1], mean(sorted));
    }

    private static long percentile(long[] sorted, int percent) {
        if (sorted.length == 0) {
            return 0L;
        }
        int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double mean(long[] sorted) {
        if (sorted.length == 0) {
            return 0.0;
        }
        long total = 0L;
        for (long sample : sorted) {
            total += sample;
        }
        return (double) total / sorted.length;
    }

    /** One command's answer to "how did it behave", in nanoseconds. */
    public record Stats(String name,
                        long ok,
                        long rejected,
                        long errors,
                        long failures,
                        long p50Nanos,
                        long p90Nanos,
                        long p99Nanos,
                        long maxNanos,
                        double meanNanos) {

        public long total() {
            return ok + rejected + errors + failures;
        }

        public static double toMillis(double nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
