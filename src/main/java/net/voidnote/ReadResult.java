package net.voidnote;

/**
 * Decrypted content of a VoidNote.
 *
 * @param content    Decrypted plaintext.
 * @param title      Note title, or {@code null} if not set.
 * @param viewCount  How many times this note has been read.
 * @param maxViews   View limit before destruction.
 * @param destroyed  {@code true} if the view limit was reached after this read.
 */
public record ReadResult(
        String content,
        String title,
        int viewCount,
        int maxViews,
        boolean destroyed) {
}
