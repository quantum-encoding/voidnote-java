package net.voidnote;

/**
 * Thrown when a VoidNote SDK operation fails.
 * Check {@link #getKind()} to distinguish error types.
 */
public class VoidNoteException extends Exception {

    public enum Kind {
        /** 404 — note or stream not found, or already destroyed. */
        NOT_FOUND,
        /** 401 — invalid or missing API key. */
        UNAUTHORIZED,
        /** Ciphertext tampered, or wrong key. */
        DECRYPTION_FAILED,
        /** HTTP transport failure. */
        NETWORK,
        /** Other server-side or crypto error. */
        API,
        /** Token string is not 64 hex chars. */
        INVALID_TOKEN,
    }

    private final Kind kind;

    public VoidNoteException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public VoidNoteException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
