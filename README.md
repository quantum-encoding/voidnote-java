# voidnote — Official Java SDK

Zero-knowledge self-destructing notes and live encrypted streams.
The key lives in the link. We never see it.

**https://voidnote.net**

Requires **Java 17+**. The only external dependency is [Gson](https://github.com/google/gson) for JSON.

---

## Install

### Maven

```xml
<dependency>
  <groupId>net.voidnote</groupId>
  <artifactId>voidnote</artifactId>
  <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'net.voidnote:voidnote:0.1.0'
```

---

## Quick start

### Read a note

```java
import net.voidnote.*;

ReadResult note = VoidNote.read("https://voidnote.net/note/<token>");
System.out.println(note.content());
System.out.printf("views: %d/%d%n", note.viewCount(), note.maxViews());
System.out.println("destroyed: " + note.destroyed());
```

### Create a note

```java
CreateResult created = VoidNote.create(
    "launch codes: 4-8-15-16-23-42",
    CreateOptions.of("vn_...").withMaxViews(1));

System.out.println("share: " + created.url());
System.out.println("expires: " + created.expiresAt());
```

### Live encrypted stream

```java
StreamHandle stream = VoidNote.createStream(
    StreamOptions.of("vn_...").withTitle("Deploy log"));

System.out.println("share: " + stream.url);

stream.write("Deployment starting...");
stream.write("Build complete — 47/47 passed");
stream.write("Service is live.");
stream.close();
```

### Watch a stream

```java
// Blocking — runs until the stream is closed
VoidNote.watch("https://voidnote.net/stream/<token>", msg -> {
    System.out.println(">> " + msg);
});
```

Or via a `StreamHandle`:

```java
stream.watch(msg -> System.out.println(msg));
```

To watch without blocking the current thread:

```java
Thread.ofVirtual().start(() -> {
    try {
        stream.watch(msg -> System.out.println(msg));
    } catch (VoidNoteException e) {
        System.err.println(e.getMessage());
    }
});
```

---

## API reference

### `VoidNote.read(urlOrToken)`

```java
ReadResult note = VoidNote.read(urlOrToken);
```

Fetches and decrypts a VoidNote. Accepts a full URL or a raw 64-character hex token.

**`ReadResult`** (record)

| Method | Type | Description |
|--------|------|-------------|
| `content()` | `String` | Decrypted plaintext |
| `title()` | `String` | Note title, or `null` |
| `viewCount()` | `int` | How many times read |
| `maxViews()` | `int` | Destruction threshold |
| `destroyed()` | `boolean` | Whether the note is gone after this read |

---

### `VoidNote.create(content, opts)`

```java
CreateResult result = VoidNote.create("secret", CreateOptions.of("vn_..."));
```

**`CreateOptions`** (immutable, fluent)

| Method | Description |
|--------|-------------|
| `CreateOptions.of(apiKey)` | Factory — only `apiKey` is required |
| `.withTitle(String)` | Optional encrypted title |
| `.withMaxViews(int)` | Destroy after N reads |
| `.withTtlMinutes(int)` | Expire after N minutes |

**`CreateResult`** (record)

| Method | Type | Description |
|--------|------|-------------|
| `url()` | `String` | Shareable URL |
| `expiresAt()` | `String` | ISO 8601 expiry timestamp |

---

### `VoidNote.createStream(opts)`

```java
StreamHandle stream = VoidNote.createStream(StreamOptions.of("vn_..."));
```

**`StreamOptions`** (immutable, fluent)

| Method | Description |
|--------|-------------|
| `StreamOptions.of(apiKey)` | Factory — TTL defaults to 3600 s |
| `.withTitle(String)` | Optional stream title |
| `.withTtl(int)` | Lifetime in seconds: `3600`, `21600`, or `86400` |

**`StreamHandle`**

| Member | Type | Description |
|--------|------|-------------|
| `url` | `String` | Shareable URL — share before writing |
| `expiresAt` | `String` | ISO 8601 expiry |
| `write(String)` | `void` | Encrypt and send a message |
| `close()` | `void` | Close the stream |
| `watch(Consumer<String>)` | `void` | Subscribe via SSE (blocking) |

---

### `VoidNote.watch(urlOrToken, onMessage)`

```java
VoidNote.watch(urlOrToken, msg -> System.out.println(msg));
```

Standalone watch — useful when you have a stream URL but didn't create the stream in this process. Auto-reconnects using SSE `Last-Event-ID`.

---

## Error handling

All methods throw `VoidNoteException` (checked). Use `getKind()` to branch on error type:

```java
try {
    ReadResult note = VoidNote.read(token);
    System.out.println(note.content());
} catch (VoidNoteException e) {
    switch (e.getKind()) {
        case NOT_FOUND         -> System.err.println("note gone or never existed");
        case UNAUTHORIZED      -> System.err.println("invalid API key");
        case DECRYPTION_FAILED -> System.err.println("tampered content or wrong key");
        case NETWORK           -> System.err.println("network error: " + e.getMessage());
        default                -> System.err.println("error: " + e.getMessage());
    }
}
```

**`VoidNoteException.Kind`**

| Variant | Meaning |
|---------|---------|
| `NOT_FOUND` | 404 — note or stream gone |
| `UNAUTHORIZED` | 401 — invalid API key |
| `DECRYPTION_FAILED` | Wrong key or tampered ciphertext |
| `NETWORK` | HTTP transport failure |
| `API` | Other server error or crypto setup failure |
| `INVALID_TOKEN` | Token is not 64 hex characters |

---

## Security model

VoidNote uses **zero-knowledge encryption** — the server never sees your plaintext.

1. A random 32-byte token is generated client-side using `SecureRandom`
2. First 16 bytes → `tokenId` (server lookup key only)
3. Last 16 bytes → `secret` → SHA-256 hashed to an AES-256 key
4. Content encrypted with AES-256-GCM before upload
5. Full 64-char hex token embedded in the shareable URL
6. Anyone with the link can decrypt; without the link, the server cannot

Crypto is 100% standard Java (`javax.crypto`, `java.security`) — no native libraries or OpenSSL.

---

## Build

```sh
git clone https://github.com/quantum-encoding/voidnote-java
cd voidnote-java
mvn package        # build jar
mvn test           # run tests
```

---

## License

MIT
