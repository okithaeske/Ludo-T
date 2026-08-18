package server;

/**
 * Server tuning, read from the command line.
 *
 * <p>The worker count and queue capacity are deliberately small by default. A server sized to
 * absorb any burst would never demonstrate a queue at all; these values let a handful of test
 * clients drive the queue visibly deep, which is the behaviour the load demonstration needs to
 * show.
 */
public record ServerConfig(int port, int workers, int queueCapacity, long metricsIntervalMillis) {

    public static final int DEFAULT_PORT = 5599;
    public static final int DEFAULT_WORKERS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 256;
    public static final long DEFAULT_METRICS_INTERVAL_MILLIS = 1_000L;

    public static ServerConfig defaults() {
        return new ServerConfig(DEFAULT_PORT, DEFAULT_WORKERS,
                DEFAULT_QUEUE_CAPACITY, DEFAULT_METRICS_INTERVAL_MILLIS);
    }

    /** Parses {@code --port=N --workers=N --queue=N --metrics=MILLIS}; unknown flags are ignored. */
    public static ServerConfig parse(String[] args) {
        ServerConfig config = defaults();
        for (String arg : args) {
            int split = arg.indexOf('=');
            if (!arg.startsWith("--") || split < 0) {
                continue;
            }
            String key = arg.substring(2, split).trim();
            String value = arg.substring(split + 1).trim();
            try {
                config = switch (key) {
                    case "port" -> new ServerConfig(Integer.parseInt(value), config.workers(),
                            config.queueCapacity(), config.metricsIntervalMillis());
                    case "workers" -> new ServerConfig(config.port(), Integer.parseInt(value),
                            config.queueCapacity(), config.metricsIntervalMillis());
                    case "queue" -> new ServerConfig(config.port(), config.workers(),
                            Integer.parseInt(value), config.metricsIntervalMillis());
                    case "metrics" -> new ServerConfig(config.port(), config.workers(),
                            config.queueCapacity(), Long.parseLong(value));
                    default -> config;
                };
            } catch (NumberFormatException e) {
                System.out.println("[server] ignoring " + arg + " (not a number)");
            }
        }
        return config;
    }
}
