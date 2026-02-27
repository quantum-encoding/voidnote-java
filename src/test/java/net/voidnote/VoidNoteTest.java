package net.voidnote;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class VoidNoteTest {

    @Test
    void tokenGeneration_is64HexChars() {
        byte[] raw = VoidNote.generateToken();
        String hex = HexFormat.of().formatHex(raw);
        assertEquals(64, hex.length());
        assertTrue(hex.matches("[0-9a-f]+"));
    }

    @Test
    void tokenGeneration_isRandom() {
        byte[] a = VoidNote.generateToken();
        byte[] b = VoidNote.generateToken();
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void keyDerivation_isDeterministic() throws VoidNoteException {
        String secret = "deadbeef".repeat(4); // 32 hex chars
        byte[] k1 = VoidNote.deriveKey(secret);
        byte[] k2 = VoidNote.deriveKey(secret);
        assertArrayEquals(k1, k2);
        assertEquals(32, k1.length); // AES-256 = 32 bytes
    }

    @Test
    void encryptDecrypt_roundtrip() throws VoidNoteException {
        String secret = "cafebabe".repeat(4);
        byte[] key = VoidNote.deriveKey(secret);
        String plaintext = "zero-knowledge self-destructing message";

        VoidNote.EncryptResult enc = VoidNote.encryptWithKey(plaintext, key);
        assertNotNull(enc.ciphertext());
        assertNotNull(enc.iv());
        assertEquals(24, enc.iv().length()); // 12 bytes → 24 hex chars

        String decrypted = VoidNote.decryptWithKey(enc.ciphertext(), enc.iv(), key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void decryption_withWrongKey_throws() throws VoidNoteException {
        String secret = "aabbccdd".repeat(4);
        byte[] rightKey = VoidNote.deriveKey(secret);
        byte[] wrongKey = VoidNote.deriveKey("11223344".repeat(4));

        VoidNote.EncryptResult enc = VoidNote.encryptWithKey("secret", rightKey);

        VoidNoteException ex = assertThrows(VoidNoteException.class,
                () -> VoidNote.decryptWithKey(enc.ciphertext(), enc.iv(), wrongKey));
        assertEquals(VoidNoteException.Kind.DECRYPTION_FAILED, ex.getKind());
    }

    @Test
    void decryption_withTamperedCiphertext_throws() throws VoidNoteException {
        String secret = "12345678".repeat(4);
        byte[] key = VoidNote.deriveKey(secret);

        VoidNote.EncryptResult enc = VoidNote.encryptWithKey("tamper me", key);

        // Flip last byte of ciphertext (GCM tag)
        String tampered = enc.ciphertext().substring(0, enc.ciphertext().length() - 2) + "ff";

        VoidNoteException ex = assertThrows(VoidNoteException.class,
                () -> VoidNote.decryptWithKey(tampered, enc.iv(), key));
        assertEquals(VoidNoteException.Kind.DECRYPTION_FAILED, ex.getKind());
    }

    @Test
    void extractToken_fromFullUrl() {
        assertEquals("abcd".repeat(16),
                VoidNote.extractToken("https://voidnote.net/note/" + "abcd".repeat(16)));
    }

    @Test
    void extractToken_rawToken() {
        String token = "1234".repeat(16);
        assertEquals(token, VoidNote.extractToken(token));
    }

    @Test
    void extractToken_urlWithFragment() {
        // Fragments appear when token is in URL hash
        assertEquals("abcd".repeat(16),
                VoidNote.extractToken("https://voidnote.net/note#" + "abcd".repeat(16)));
    }

    @Test
    void invalidApiKey_throws() {
        VoidNoteException ex = assertThrows(VoidNoteException.class,
                () -> VoidNote.create("secret", CreateOptions.of("")));
        assertEquals(VoidNoteException.Kind.API, ex.getKind());
    }

    @Test
    void invalidToken_throws() {
        VoidNoteException ex = assertThrows(VoidNoteException.class,
                () -> VoidNote.read("tooshort"));
        assertEquals(VoidNoteException.Kind.INVALID_TOKEN, ex.getKind());
    }
}
