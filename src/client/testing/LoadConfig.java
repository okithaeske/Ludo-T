package client.testing;

import java.util.Locale;

/**
 * Everything a load run needs, parsed from {@code --key=value} flags.
 *
 * <p>Same flag shape as {@link server.ServerConfig} on purpose: one convention across both
 * tiers means a demo script reads the same way whichever process it is starting.
 *
 * <p>The defaults are chosen to be the rubric's sentence made runnable — <em>two test clients
 * sending asynchronous requests in rapid succession</em> — and to finish in a few seconds so
 * it can be run live rather than described.
 */
public record LoadConfig(String host,
                         int port,
                         int clients,
                         int threads,
                         int requestsPerThread,
                         long durationSeconds,
                         int games,
                         String mode,
                         long tickMillis,
                         Workload mix,
                         int inFlightPerClient,
                         long seed,
                         boolean subscribe,
                         boolean startGames,
                         boolean keepGames,
                         long sampleMillis,
                         String csvPath) {

    public static LoadConfig defaults() {
        return new LoadConfig("localhost", 5599,
                2,      // clients — the rubric's "two test clients", each its own socket
                4,      // threads per client — async requests from one client, concurrently
                250,    // requests per thread
                0L,     // duration: 0 means "until the request budget is spent"
                2,      // games to create and hammer
                "LUDO_T", 250L,
                Workload.MIXED,
                64,     // in-flight cap per client — see LoadClient for why this exists
                20261009L,
                true,   // subscribe: also measure the push half
                true,   // start the games, so actors are busy while requests arrive
                false,  // abort what we created on the way out
                100L,   // metrics sampling interval; short runs need frequent samples
                null);
    }

    /** Parses the documented flags; anything unrecognised is ignored rather than fatal. */
    public static LoadConfig parse(String[] args) {
        LoadConfig config = defaults();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int split = arg.indexOf('=');
            String key = (split < 0 ? arg.substring(2) : arg.substring(2, split)).trim();
            String value = split < 0 ? "true" : arg.substring(split + 1).trim();
            try {
                config = apply(config, key, value);
            } catch (NumberFormatException e) {
                System.out.println("[load] ignoring " + arg + " (not a number)");
            }
        }
        return config;
    }

    private static LoadConfig apply(LoadConfig c, String key, String value) {
        return switch (key) {
            case "host" -> c.withHost(value);
            case "port" -> c.withPort(Integer.parseInt(value));
            case "clients" -> c.withClients(Integer.parseInt(value));
            case "threads" -> c.withThreads(Integer.parseInt(value));
            case "requests" -> c.withRequestsPerThread(Integer.parseInt(value));
            case "duration" -> c.withDurationSeconds(Long.parseLong(value));
            case "games" -> c.withGames(Integer.parseInt(value));
            case "mode" -> c.withMode(value.toUpperCase(Locale.ROOT));
            case "tick" -> c.withTickMillis(Long.parseLong(value));
            case "mix" -> c.withMix(Workload.parse(value));
            case "inflight" -> c.withInFlightPerClient(Integer.parseInt(value));
            case "seed" -> c.withSeed(Long.parseLong(value));
            case "subscribe" -> c.withSubscribe(Boolean.parseBoolean(value));
            case "start" -> c.withStartGames(Boolean.parseBoolean(value));
            case "keep" -> c.withKeepGames(Boolean.parseBoolean(value));
            case "sample" -> c.withSampleMillis(Long.parseLong(value));
            case "csv" -> c.withCsvPath(value);
            default -> c;
        };
    }

    /** Total requests the run will attempt, ignoring an early stop on {@code --duration}. */
    public long plannedRequests() {
        return (long) clients * threads * requestsPerThread;
    }

    public static String usage() {
        return """
                Usage: java -cp out client.testing.TestClientMain [flags]

                  --host=localhost     server host
                  --port=5599          server port
                  --clients=2          independent connections (each its own socket + threads)
                  --threads=4          request threads per client
                  --requests=250       requests per thread (0 = unlimited, needs --duration)
                  --duration=0         seconds to run; 0 means until the request budget is spent
                  --games=2            games to create and drive
                  --mode=LUDO_T        CLASSIC or LUDO_T
                  --tick=250           round interval of the created games, in ms
                  --mix=MIXED          READ | MIXED | CONTROL  (see Workload)
                  --inflight=64        outstanding requests allowed per client
                  --seed=20261009      makes the command sequence reproducible
                  --subscribe=true     also subscribe, to measure the push half
                  --start=true         start the created games
                  --keep=false         leave the created games running afterwards
                  --sample=100         how often to sample server metrics, in ms
                  --csv=path.csv       append one summary row to this file
                """;
    }

    // ── Wither methods; a record's canonical constructor is too wide to call repeatedly ──

    private LoadConfig withHost(String v) {
        return new LoadConfig(v, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withPort(int v) {
        return new LoadConfig(host, v, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withClients(int v) {
        return new LoadConfig(host, port, v, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withThreads(int v) {
        return new LoadConfig(host, port, clients, v, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withRequestsPerThread(int v) {
        return new LoadConfig(host, port, clients, threads, v, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withDurationSeconds(long v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, v,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withGames(int v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                v, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withMode(String v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, v, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withTickMillis(long v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, v, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withMix(Workload v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, v, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withInFlightPerClient(int v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, v, seed, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withSeed(long v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, v, subscribe, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withSubscribe(boolean v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, v, startGames,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withStartGames(boolean v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, v,
                keepGames, sampleMillis, csvPath);
    }

    private LoadConfig withKeepGames(boolean v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                v, sampleMillis, csvPath);
    }

    private LoadConfig withSampleMillis(long v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, v, csvPath);
    }

    private LoadConfig withCsvPath(String v) {
        return new LoadConfig(host, port, clients, threads, requestsPerThread, durationSeconds,
                games, mode, tickMillis, mix, inFlightPerClient, seed, subscribe, startGames,
                keepGames, sampleMillis, v);
    }
}
