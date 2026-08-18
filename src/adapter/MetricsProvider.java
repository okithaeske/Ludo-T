package adapter;

import shared.ServerMetricsDto;

/**
 * Supplies the current server load. Implemented by the network layer, which owns the queue and
 * the connections that the numbers describe.
 */
@FunctionalInterface
public interface MetricsProvider {

    ServerMetricsDto currentMetrics();
}
