package persistence;

/**
 * How to reach the database tier, read from the same {@code --key=value} flags the other two
 * tiers use.
 *
 * <p>The default URL points at the H2 process {@code run-db.ps1} starts. It is a
 * {@code tcp://} URL rather than a file path on purpose: an embedded database would make the
 * store a library inside the server process, and the whole point of this tier is that it runs
 * as a third application.
 *
 * @param queueCapacity how many un-written rows may pile up before the writer starts dropping
 *                      them. Bounded for the same reason every other queue in this system is:
 *                      an unbounded one turns a stopped database into an out-of-memory crash
 *                      of the server, which is a far worse failure than a missing history row.
 */
public record DatabaseConfig(String url, String user, String password, int queueCapacity) {

    public static final String DEFAULT_URL = "jdbc:h2:tcp://localhost:9092/ludo";
    public static final String DEFAULT_USER = "sa";
    public static final String DEFAULT_PASSWORD = "";
    public static final int DEFAULT_QUEUE_CAPACITY = 10_000;

    public static DatabaseConfig defaults() {
        return new DatabaseConfig(DEFAULT_URL, DEFAULT_USER, DEFAULT_PASSWORD,
                DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Parses {@code --db=URL --dbuser=NAME --dbpassword=PWD --dbqueue=N}.
     *
     * <p>{@code --db=off} is understood by {@link server.ServerMain} as "run with no database
     * tier at all", which is how the server is started when only the concurrency behaviour is
     * being demonstrated.
     */
    public static DatabaseConfig parse(String[] args) {
        DatabaseConfig config = defaults();
        for (String arg : args) {
            int split = arg.indexOf('=');
            if (!arg.startsWith("--") || split < 0) {
                continue;
            }
            String key = arg.substring(2, split).trim();
            String value = arg.substring(split + 1).trim();
            config = switch (key) {
                case "db" -> new DatabaseConfig(value, config.user(), config.password(),
                        config.queueCapacity());
                case "dbuser" -> new DatabaseConfig(config.url(), value, config.password(),
                        config.queueCapacity());
                case "dbpassword" -> new DatabaseConfig(config.url(), config.user(), value,
                        config.queueCapacity());
                case "dbqueue" -> new DatabaseConfig(config.url(), config.user(),
                        config.password(), parseCapacity(value, config.queueCapacity()));
                default -> config;
            };
        }
        return config;
    }

    private static int parseCapacity(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.out.println("[db] ignoring --dbqueue=" + value + " (not a number)");
            return fallback;
        }
    }

    /** True when the operator asked for no database tier. */
    public boolean isDisabled() {
        return url == null || url.isBlank() || "off".equalsIgnoreCase(url);
    }
}
