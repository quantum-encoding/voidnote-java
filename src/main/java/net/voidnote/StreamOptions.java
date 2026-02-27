package net.voidnote;

/**
 * Options for {@link VoidNote#createStream}.
 *
 * <pre>{@code
 * StreamOptions opts = StreamOptions.of("vn_...")
 *     .withTitle("Deploy log")
 *     .withTtl(3600);
 * }</pre>
 */
public final class StreamOptions {

    /** Required: your API key ({@code vn_...}). */
    public final String apiKey;

    /** Optional stream title. */
    public final String title;

    /**
     * Stream lifetime in seconds.
     * Accepted values: {@code 3600} (1h), {@code 21600} (6h), {@code 86400} (24h).
     * Defaults to {@code 3600}.
     */
    public final int ttlSeconds;

    private StreamOptions(String apiKey, String title, int ttlSeconds) {
        this.apiKey = apiKey;
        this.title = title;
        this.ttlSeconds = ttlSeconds;
    }

    /** Creates options with only the required API key. TTL defaults to 3600 s. */
    public static StreamOptions of(String apiKey) {
        return new StreamOptions(apiKey, null, 3600);
    }

    public StreamOptions withTitle(String title) {
        return new StreamOptions(apiKey, title, ttlSeconds);
    }

    public StreamOptions withTtl(int ttlSeconds) {
        return new StreamOptions(apiKey, title, ttlSeconds);
    }
}
