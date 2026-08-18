package test;

import net.RequestQueue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@DisplayName("RequestQueue")
class RequestQueueTest {

    @Test
    @DisplayName("should_runEveryTask_when_submittedFromManyThreadsAtOnce")
    void should_runEveryTask_when_submittedFromManyThreadsAtOnce() throws Exception {
        // Arrange — far more work than workers or queue slots, so saturation is guaranteed.
        int producers = 8;
        int perProducer = 500;
        int total = producers * perProducer;

        LongAdder executed = new LongAdder();
        CountDownLatch done = new CountDownLatch(total);

        try (RequestQueue queue = new RequestQueue(4, 16)) {
            ExecutorService clients = Executors.newFixedThreadPool(producers);
            CountDownLatch startTogether = new CountDownLatch(1);

            for (int p = 0; p < producers; p++) {
                clients.execute(() -> {
                    awaitQuietly(startTogether);
                    for (int i = 0; i < perProducer; i++) {
                        queue.submit(() -> {
                            executed.increment();
                            done.countDown();
                        });
                    }
                });
            }

            // Act
            startTogether.countDown();
            boolean finished = done.await(60, TimeUnit.SECONDS);
            clients.shutdownNow();

            // Assert — the whole point of the saturation policy: nothing is ever dropped.
            Assertions.assertTrue(finished, "Queue did not drain within the timeout");
            Assertions.assertEquals(total, executed.sum(),
                    "Requests were lost under load — they must be queued or run inline, never discarded");
            Assertions.assertEquals(total, queue.acceptedCount());
            Assertions.assertEquals(total, queue.completedCount());
        }
    }

    @Test
    @DisplayName("should_recordSaturation_when_queueFillsBeyondCapacity")
    void should_recordSaturation_when_queueFillsBeyondCapacity() throws Exception {
        // Arrange — one worker, held busy, and a queue of two: the rest must saturate.
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch workerBusy = new CountDownLatch(1);
        AtomicInteger ran = new AtomicInteger();

        try (RequestQueue queue = new RequestQueue(1, 2)) {
            queue.submit(() -> {
                workerBusy.countDown();
                awaitQuietly(release);
                ran.incrementAndGet();
            });
            Assertions.assertTrue(workerBusy.await(10, TimeUnit.SECONDS));

            // Act — 6 more with one worker blocked and only 2 queue slots
            for (int i = 0; i < 6; i++) {
                queue.submit(ran::incrementAndGet);
            }

            // Saturation is observable before anything is released: the submitting thread ran
            // the overflow inline rather than the queue accepting or discarding it.
            Assertions.assertTrue(queue.saturationCount() > 0,
                    "Queue should have reported saturation once it filled");

            release.countDown();

            // Assert — the blocked worker and the two queued tasks still have to finish, so
            // wait for the queue to drain rather than reading the counter immediately.
            long deadline = System.currentTimeMillis() + 30_000;
            while (queue.completedCount() < 7 && System.currentTimeMillis() < deadline) {
                Thread.sleep(5);
            }
            Assertions.assertEquals(7, ran.get(), "Every task must still run");
            Assertions.assertEquals(7, queue.completedCount());
        }
    }

    @Test
    @DisplayName("should_reportZeroDepth_when_noWorkIsPending")
    void should_reportZeroDepth_when_noWorkIsPending() {
        try (RequestQueue queue = new RequestQueue(2, 8)) {
            Assertions.assertEquals(0, queue.depth());
            Assertions.assertEquals(8, queue.capacity());
            Assertions.assertEquals(0, queue.acceptedCount());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
