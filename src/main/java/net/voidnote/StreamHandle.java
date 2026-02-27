package net.voidnote;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

/**
 * A live Void Stream. Write encrypted messages and {@link #close()} to self-destruct.
 * Obtained from {@link VoidNote#createStream(StreamOptions)}.
 *
 * <pre>{@code
 * StreamHandle stream = VoidNote.createStream(StreamOptions.of("vn_..."));
 * System.out.println(stream.url);   // share this before writing
 *
 * stream.write("Deployment starting...");
 * stream.write("Build complete — 47/47 passed");
 * stream.close();
 * }</pre>
 */
public final class StreamHandle {

    /** Shareable URL. Share this with viewers before writing messages. */
    public final String url;

    /** ISO 8601 expiry timestamp. */
    public final String expiresAt;

    private final String fullToken;
    private final byte[] key;   // pre-derived AES-256 key
    private final String base;

    StreamHandle(String url, String expiresAt, String fullToken, byte[] key, String base) {
        this.url = url;
        this.expiresAt = expiresAt;
        this.fullToken = fullToken;
        this.key = key;
        this.base = base;
    }

    /**
     * Encrypts {@code content} client-side and pushes it to the stream.
     * The server never sees the plaintext.
     */
    public void write(String content) throws VoidNoteException {
        VoidNote.EncryptResult enc = VoidNote.encryptWithKey(content, key);
        JsonObject body = new JsonObject();
        body.addProperty("encryptedContent", enc.ciphertext());
        body.addProperty("iv", enc.iv());
        VoidNote.httpPost(base + "/api/stream/" + fullToken + "/write",
                VoidNote.GSON.toJson(body), null);
    }

    /**
     * Closes the stream. Viewers receive a {@code closed} event and all
     * content self-destructs.
     */
    public void close() throws VoidNoteException {
        VoidNote.httpPost(base + "/api/stream/" + fullToken + "/close", "{}", null);
    }

    /**
     * Subscribes to this stream, calling {@code onMessage} for each decrypted event.
     * Blocks until the stream is closed or an unrecoverable error occurs.
     * Automatically reconnects on connection drop using SSE {@code Last-Event-ID}.
     *
     * <p>To run non-blocking, wrap in a thread:
     * <pre>{@code
     * Thread.ofVirtual().start(() -> stream.watch(msg -> System.out.println(msg)));
     * }</pre>
     */
    public void watch(Consumer<String> onMessage) throws VoidNoteException {
        VoidNote.watchSse(base + "/api/stream/" + fullToken + "/events", key, onMessage);
    }
}
