package net.voidnote;

/**
 * Options for {@link VoidNote#create}.
 *
 * <pre>{@code
 * CreateOptions opts = CreateOptions.of("vn_...")
 *     .withMaxViews(1)
 *     .withExpiresIn(6)      // hours: 1, 6, 24, 72, 168, 720
 *     .withNoteType("pipe")  // "secure" (default) or "pipe"
 *     .withTitle("Deploy token");
 * }</pre>
 */
public final class CreateOptions {

    private static final java.util.Set<Integer> ALLOWED_EXPIRY_HOURS =
            java.util.Set.of(1, 6, 24, 72, 168, 720);

    /** Required: your API key ({@code vn_...}). */
    public final String apiKey;

    /** Optional note title (stored in plaintext). */
    public final String title;

    /** Destroy after this many reads (1–100). {@code null} = server default (1). */
    public final Integer maxViews;

    /**
     * Expiry in hours. Must be one of: 1, 6, 24, 72, 168, 720.
     * {@code null} = server default (24h).
     */
    public final Integer expiresIn;

    /**
     * Note type: {@code "secure"} (default, encrypted static note) or
     * {@code "pipe"} (streaming append mode).
     * {@code null} = server default ("secure").
     */
    public final String noteType;

    private CreateOptions(String apiKey, String title, Integer maxViews,
                          Integer expiresIn, String noteType) {
        this.apiKey = apiKey;
        this.title = title;
        this.maxViews = maxViews;
        this.expiresIn = expiresIn;
        this.noteType = noteType;
    }

    /** Creates options with only the required API key. */
    public static CreateOptions of(String apiKey) {
        return new CreateOptions(apiKey, null, null, null, null);
    }

    public CreateOptions withTitle(String title) {
        return new CreateOptions(apiKey, title, maxViews, expiresIn, noteType);
    }

    public CreateOptions withMaxViews(int maxViews) {
        return new CreateOptions(apiKey, title, maxViews, expiresIn, noteType);
    }

    /**
     * Set expiry in hours. Must be one of: 1, 6, 24, 72, 168, 720.
     *
     * @throws IllegalArgumentException if the value is not in the allowed set.
     */
    public CreateOptions withExpiresIn(int hours) {
        if (!ALLOWED_EXPIRY_HOURS.contains(hours)) {
            throw new IllegalArgumentException(
                    "expiresIn must be one of 1, 6, 24, 72, 168, 720 — got " + hours);
        }
        return new CreateOptions(apiKey, title, maxViews, hours, noteType);
    }

    /**
     * Set note type: {@code "secure"} or {@code "pipe"}.
     *
     * @throws IllegalArgumentException if the value is not recognised.
     */
    public CreateOptions withNoteType(String noteType) {
        if (!"secure".equals(noteType) && !"pipe".equals(noteType)) {
            throw new IllegalArgumentException(
                    "noteType must be \"secure\" or \"pipe\" — got \"" + noteType + "\"");
        }
        return new CreateOptions(apiKey, title, maxViews, expiresIn, noteType);
    }
}
