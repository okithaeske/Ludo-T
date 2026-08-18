package shared;

import java.io.Serializable;

/**
 * Live server load. Pushed to subscribed clients so the queue can be <em>seen</em> filling
 * and draining while the test clients fire, rather than merely asserted in a report.
 */
public record ServerMetricsDto(int queueDepth,
                               int queueCapacity,
                               int activeWorkers,
                               int poolSize,
                               long acceptedRequests,
                               long completedRequests,
                               long rejectedRequests,
                               int connectedClients,
                               int liveSessions) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Queue fill as a fraction in {@code [0,1]} — drives the GUI's load bar. */
    public double queueLoad() {
        return queueCapacity == 0 ? 0.0 : (double) queueDepth / queueCapacity;
    }

    /** Requests accepted but not yet completed. */
    public long inFlight() {
        return acceptedRequests - completedRequests;
    }
}
