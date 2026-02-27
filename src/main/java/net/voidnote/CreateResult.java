package net.voidnote;

/**
 * Result of {@link VoidNote#create}.
 *
 * @param url       Shareable URL. The decryption key is embedded in the URL path —
 *                  share the full URL with the recipient.
 * @param expiresAt ISO 8601 expiry timestamp.
 */
public record CreateResult(String url, String expiresAt) {
}
