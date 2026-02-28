package net.voidnote;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Official Java SDK for VoidNote.
 *
 * <p>Zero-knowledge self-destructing notes and live encrypted streams.
 * The key lives in the link. We never see it.
 *
 * <p>Requires Java 17+. The only external dependency is Gson for JSON.
 *
 * <pre>{@code
 * // Read a note
 * ReadResult note = VoidNote.read("https://voidnote.net/note/<token>");
 * System.out.println(note.content());
 *
 * // Create a note (destroyed after 1 read)
 * CreateResult created = VoidNote.create(
 *     "launch codes: 4-8-15-16-23-42",
 *     CreateOptions.of("vn_...").withMaxViews(1));
 * System.out.println(created.url());
 *
 * // Live encrypted stream
 * StreamHandle stream = VoidNote.createStream(StreamOptions.of("vn_..."));
 * System.out.println("share: " + stream.url);
 * stream.write("Deployment starting...");
 * stream.write("All 47 tests passed");
 * stream.close();
 * }</pre>
 */
public final class VoidNote {

    static final String DEFAULT_BASE = "https://voidnote.net";
    static final Gson GSON = new Gson();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private VoidNote() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches and decrypts a VoidNote.
     *
     * @param urlOrToken Full URL ({@code https://voidnote.net/note/<token>}) or raw 64-char hex token.
     */
    public static ReadResult read(String urlOrToken) throws VoidNoteException {
        return readFrom(urlOrToken, DEFAULT_BASE);
    }

    /** Like {@link #read} but with a custom API base URL (for self-hosted instances). */
    public static ReadResult readFrom(String urlOrToken, String base) throws VoidNoteException {
        String token = extractToken(urlOrToken);
        if (token.length() != 64) {
            throw new VoidNoteException(VoidNoteException.Kind.INVALID_TOKEN,
                    "invalid token length " + token.length() + " (want 64)");
        }
        String tokenId = token.substring(0, 32);
        String secret  = token.substring(32);

        String body = httpGet(base + "/api/note/" + tokenId);

        NotePayload p = GSON.fromJson(body, NotePayload.class);
        String content = decrypt(p.encrypted_content, p.iv, secret);

        return new ReadResult(content, p.title, p.view_count, p.max_views, p.destroyed);
    }

    /**
     * Encrypts {@code content} client-side and creates a self-destructing note.
     *
     * @param content The secret plaintext to protect.
     * @param opts    Options including the required API key.
     */
    public static CreateResult create(String content, CreateOptions opts) throws VoidNoteException {
        requireApiKey(opts.apiKey);

        byte[] raw = generateToken();
        String hex     = HexFormat.of().formatHex(raw);
        String tokenId = hex.substring(0, 32);
        String secret  = hex.substring(32);

        EncryptResult enc = encryptContent(content, secret);

        JsonObject req = new JsonObject();
        req.addProperty("tokenId", tokenId);
        req.addProperty("encryptedContent", enc.ciphertext());
        req.addProperty("iv", enc.iv());
        if (opts.maxViews  != null) req.addProperty("maxViews",  opts.maxViews);
        if (opts.expiresIn != null) req.addProperty("expiresIn", opts.expiresIn);
        if (opts.noteType  != null) req.addProperty("noteType",  opts.noteType);
        if (opts.title     != null) req.addProperty("title",     opts.title);

        String resp = httpPost(DEFAULT_BASE + "/api/notes", GSON.toJson(req), opts.apiKey);
        CreatePayload p = GSON.fromJson(resp, CreatePayload.class);

        return new CreateResult(
                DEFAULT_BASE + "/note/" + hex,
                p.expires_at != null ? p.expires_at : "");
    }

    /**
     * Opens a live encrypted stream.
     *
     * @param opts Options including the required API key.
     * @return A {@link StreamHandle} — share {@code handle.url} with viewers before writing.
     */
    public static StreamHandle createStream(StreamOptions opts) throws VoidNoteException {
        requireApiKey(opts.apiKey);

        byte[] raw = generateToken();
        String hex     = HexFormat.of().formatHex(raw);
        String tokenId = hex.substring(0, 32);
        String secret  = hex.substring(32);
        byte[] key     = deriveKey(secret);

        JsonObject req = new JsonObject();
        req.addProperty("tokenId", tokenId);
        req.addProperty("ttl", opts.ttlSeconds);
        if (opts.title != null) req.addProperty("title", opts.title);

        String resp = httpPost(DEFAULT_BASE + "/api/stream", GSON.toJson(req), opts.apiKey);
        StreamPayload p = GSON.fromJson(resp, StreamPayload.class);

        String siteUrl = p.siteUrl != null ? p.siteUrl : DEFAULT_BASE;
        String url     = siteUrl + "/stream/" + hex;
        String expiresAt = p.expiresAt != null ? p.expiresAt : "";

        return new StreamHandle(url, expiresAt, hex, key, DEFAULT_BASE);
    }

    /**
     * Watches a stream, calling {@code onMessage} for each decrypted event.
     * Blocks until the stream is closed. Auto-reconnects on drop.
     *
     * @param urlOrToken Full stream URL or raw 64-char hex token.
     * @param onMessage  Called with decrypted plaintext for each incoming message.
     */
    public static void watch(String urlOrToken, Consumer<String> onMessage) throws VoidNoteException {
        String token = extractToken(urlOrToken);
        if (token.length() != 64) {
            throw new VoidNoteException(VoidNoteException.Kind.INVALID_TOKEN,
                    "invalid token length " + token.length() + " (want 64)");
        }
        byte[] key = deriveKey(token.substring(32));
        watchSse(DEFAULT_BASE + "/api/stream/" + token + "/events", key, onMessage);
    }

    // -------------------------------------------------------------------------
    // SSE (Server-Sent Events)
    // -------------------------------------------------------------------------

    static void watchSse(String sseUrl, byte[] key, Consumer<String> onMessage)
            throws VoidNoteException {
        String lastId = "";

        while (true) {
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .GET();
                if (!lastId.isEmpty()) rb.header("Last-Event-ID", lastId);

                // SSE connections are long-lived — no read timeout
                HttpClient sseClient = HttpClient.newHttpClient();
                HttpResponse<Stream<String>> resp =
                        sseClient.send(rb.build(), HttpResponse.BodyHandlers.ofLines());

                String eventId = "";
                StringBuilder eventData = new StringBuilder();

                for (String line : (Iterable<String>) resp.body()::iterator) {
                    if (line.startsWith("id: ")) {
                        eventId = line.substring(4);
                    } else if (line.startsWith("data: ")) {
                        eventData.append(line.substring(6));
                    } else if (line.isEmpty() && eventData.length() > 0) {
                        if (!eventId.isEmpty()) lastId = eventId;

                        JsonObject data = GSON.fromJson(eventData.toString(), JsonObject.class);
                        String type = data.has("type") ? data.get("type").getAsString() : "";

                        if ("closed".equals(type) || "expired".equals(type)) {
                            return;
                        }

                        if (data.has("enc")) {
                            String enc = data.get("enc").getAsString();
                            String iv  = data.get("iv").getAsString();
                            String content = decryptWithKey(enc, iv, key);
                            onMessage.accept(content);
                        }

                        eventId = "";
                        eventData.setLength(0);
                    }
                }
            } catch (VoidNoteException e) {
                throw e;
            } catch (Exception e) {
                // Connection dropped — wait and reconnect
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Crypto
    // -------------------------------------------------------------------------

    static byte[] generateToken() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return raw;
    }

    static byte[] deriveKey(String secret) throws VoidNoteException {
        try {
            byte[] secretBytes = HexFormat.of().parseHex(secret);
            return MessageDigest.getInstance("SHA-256").digest(secretBytes);
        } catch (Exception e) {
            throw new VoidNoteException(VoidNoteException.Kind.API, "key derivation failed", e);
        }
    }

    record EncryptResult(String ciphertext, String iv) {}

    static EncryptResult encryptContent(String plaintext, String secret) throws VoidNoteException {
        return encryptWithKey(plaintext, deriveKey(secret));
    }

    static EncryptResult encryptWithKey(String plaintext, byte[] key) throws VoidNoteException {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            // Java's AES/GCM appends the 16-byte tag at end of ciphertext — same layout as Go's gcm.Seal
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            HexFormat hex = HexFormat.of();
            return new EncryptResult(hex.formatHex(ciphertext), hex.formatHex(iv));
        } catch (Exception e) {
            throw new VoidNoteException(VoidNoteException.Kind.API, "encryption failed", e);
        }
    }

    static String decrypt(String encryptedHex, String ivHex, String secret) throws VoidNoteException {
        return decryptWithKey(encryptedHex, ivHex, deriveKey(secret));
    }

    static String decryptWithKey(String encryptedHex, String ivHex, byte[] key)
            throws VoidNoteException {
        try {
            HexFormat hex = HexFormat.of();
            byte[] ciphertext = hex.parseHex(encryptedHex);
            byte[] iv         = hex.parseHex(ivHex);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new VoidNoteException(VoidNoteException.Kind.DECRYPTION_FAILED,
                    "decryption failed — tampered data or wrong key", e);
        } catch (Exception e) {
            throw new VoidNoteException(VoidNoteException.Kind.DECRYPTION_FAILED,
                    "decryption failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    static String httpGet(String url) throws VoidNoteException {
        try {
            HttpResponse<String> resp = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .timeout(Duration.ofSeconds(15))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            return checkStatus(resp);
        } catch (VoidNoteException e) {
            throw e;
        } catch (Exception e) {
            throw new VoidNoteException(VoidNoteException.Kind.NETWORK, "request failed", e);
        }
    }

    /** @param apiKey may be {@code null} for unauthenticated endpoints. */
    static String httpPost(String url, String body, String apiKey) throws VoidNoteException {
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15));
            if (apiKey != null && !apiKey.isEmpty()) {
                rb.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            return checkStatus(resp);
        } catch (VoidNoteException e) {
            throw e;
        } catch (Exception e) {
            throw new VoidNoteException(VoidNoteException.Kind.NETWORK, "request failed", e);
        }
    }

    private static String checkStatus(HttpResponse<String> resp) throws VoidNoteException {
        return switch (resp.statusCode()) {
            case 200, 201 -> resp.body();
            case 404 -> throw new VoidNoteException(VoidNoteException.Kind.NOT_FOUND,
                    "not found or already destroyed");
            case 401 -> throw new VoidNoteException(VoidNoteException.Kind.UNAUTHORIZED,
                    "unauthorized — check your API key");
            default  -> throw new VoidNoteException(VoidNoteException.Kind.API,
                    "server returned " + resp.statusCode() + ": " + resp.body());
        };
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    static String extractToken(String urlOrToken) {
        if (urlOrToken.startsWith("http")) {
            String[] parts = urlOrToken.split("/");
            String last = parts[parts.length - 1];
            // Strip fragment if present (e.g. /note/abc123#fragment)
            int hash = last.indexOf('#');
            if (hash >= 0) last = last.substring(hash + 1);
            return last;
        }
        return urlOrToken;
    }

    private static void requireApiKey(String apiKey) throws VoidNoteException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new VoidNoteException(VoidNoteException.Kind.API, "apiKey is required");
        }
    }

    // -------------------------------------------------------------------------
    // Private Gson deserialization helpers
    // -------------------------------------------------------------------------

    private static class NotePayload {
        String encrypted_content;
        String iv;
        String title;
        int view_count;
        int max_views;
        boolean destroyed;
    }

    private static class CreatePayload {
        String expires_at;
    }

    private static class StreamPayload {
        String siteUrl;
        String expiresAt;
    }

    private static class CryptoOrderPayload {
        String orderId;
        String toAddress;
        String chain;
        String token;
        double amountUsd;
        String amount;
        int credits;
        String expiresAt;
    }

    private static class SubmitPaymentPayload {
        boolean ok;
        int credits;
        int creditsAdded;
    }

    // -------------------------------------------------------------------------
    // Credits / buy API
    // -------------------------------------------------------------------------

    /**
     * Creates a crypto payment order. Returns addressing details for the on-chain transfer.
     *
     * <p>Supported bundles: {@code "test"} ($1/20 notes), {@code "starter"} ($5/100),
     * {@code "standard"} ($20/500), {@code "pro"} ($35/1000).
     *
     * <p>Supported chains: {@code "polygon"}, {@code "base"}, {@code "arbitrum"},
     * {@code "ethereum"}, {@code "bitcoin"}, {@code "tron"}.
     *
     * <p>Supported tokens depend on chain — stablecoins ({@code "USDT"}, {@code "USDC"}),
     * native ({@code "ETH"}, {@code "BTC"}, {@code "TRX"}).
     *
     * @param apiKey   Your API key ({@code vn_...}).
     * @param bundleId One of: {@code "test"}, {@code "starter"}, {@code "standard"}, {@code "pro"}.
     * @param chain    Target chain.
     * @param token    Token to pay with.
     * @return A {@link CryptoOrder} containing the recipient address and exact amount.
     */
    public static CryptoOrder createCryptoOrder(
            String apiKey, String bundleId, String chain, String token)
            throws VoidNoteException {
        requireApiKey(apiKey);
        JsonObject req = new JsonObject();
        req.addProperty("bundleId", bundleId);
        req.addProperty("chain", chain);
        req.addProperty("token", token);

        String resp = httpPost(DEFAULT_BASE + "/api/buy/crypto/create-order",
                GSON.toJson(req), apiKey);
        CryptoOrderPayload p = GSON.fromJson(resp, CryptoOrderPayload.class);
        return new CryptoOrder(p.orderId, p.toAddress, p.chain, p.token,
                p.amountUsd, p.amount, p.credits, p.expiresAt);
    }

    /**
     * Submits a completed on-chain transaction for verification.
     * If valid, credits are added to your account immediately.
     *
     * @param apiKey  Your API key ({@code vn_...}).
     * @param orderId The order ID returned by {@link #createCryptoOrder}.
     * @param txHash  The on-chain transaction hash / txid.
     * @return A {@link SubmitPaymentResult} with the updated credit balance.
     */
    public static SubmitPaymentResult submitCryptoPayment(
            String apiKey, String orderId, String txHash)
            throws VoidNoteException {
        requireApiKey(apiKey);
        JsonObject req = new JsonObject();
        req.addProperty("orderId", orderId);
        req.addProperty("txHash", txHash);

        String resp = httpPost(DEFAULT_BASE + "/api/buy/crypto/submit-tx",
                GSON.toJson(req), apiKey);
        SubmitPaymentPayload p = GSON.fromJson(resp, SubmitPaymentPayload.class);
        return new SubmitPaymentResult(p.credits, p.creditsAdded);
    }
}
