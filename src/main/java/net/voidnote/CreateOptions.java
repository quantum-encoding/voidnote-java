package net.voidnote;

/**
 * Options for {@link VoidNote#create}.
 *
 * <pre>{@code
 * CreateOptions opts = CreateOptions.of("vn_...")
 *     .withMaxViews(1)
 *     .withTitle("Deploy token");
 * }</pre>
 */
public final class CreateOptions {

    /** Required: your API key ({@code vn_...}). */
    public final String apiKey;

    /** Optional note title (stored encrypted). */
    public final String title;

    /** Destroy after this many reads. {@code null} = server default. */
    public final Integer maxViews;

    /** Expire after this many minutes. {@code null} = server default. */
    public final Integer ttlMinutes;

    private CreateOptions(String apiKey, String title, Integer maxViews, Integer ttlMinutes) {
        this.apiKey = apiKey;
        this.title = title;
        this.maxViews = maxViews;
        this.ttlMinutes = ttlMinutes;
    }

    /** Creates options with only the required API key. */
    public static CreateOptions of(String apiKey) {
        return new CreateOptions(apiKey, null, null, null);
    }

    public CreateOptions withTitle(String title) {
        return new CreateOptions(apiKey, title, maxViews, ttlMinutes);
    }

    public CreateOptions withMaxViews(int maxViews) {
        return new CreateOptions(apiKey, title, maxViews, ttlMinutes);
    }

    public CreateOptions withTtlMinutes(int ttlMinutes) {
        return new CreateOptions(apiKey, title, maxViews, ttlMinutes);
    }
}
