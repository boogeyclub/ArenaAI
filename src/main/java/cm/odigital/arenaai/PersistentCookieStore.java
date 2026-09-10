package cm.odigital.arenaai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed {@link CookieStore} so login sessions survive app restarts.
 *
 * <p>JavaFX WebView only ships an in-memory cookie store. This implementation
 * mirrors every mutation to a small binary file and reloads it on the next
 * launch. {@code Max-Age} cookies are aged by the time spent away; expired
 * ones are dropped on load.</p>
 *
 * <p>All methods are synchronized: WebView touches the store from both the
 * FX thread and background network threads.</p>
 */
public final class PersistentCookieStore implements CookieStore {

    private static final int FILE_VERSION = 1;

    private final Path file;
    private final List<HttpCookie> cookies = new ArrayList<>();
    private final Map<HttpCookie, URI> origins = new HashMap<>();
    /** Creation timestamps (epoch millis), used to age Max-Age cookies across restarts. */
    private final Map<HttpCookie, Long> createdAt = new HashMap<>();

    public PersistentCookieStore() {
        this(CookiePersistence.defaultCookieFile());
    }

    public PersistentCookieStore(Path file) {
        this.file = file;
        loadFromFile();
    }

    // --- CookieStore API ---

    @Override
    public synchronized void add(URI uri, HttpCookie cookie) {
        if (cookie == null) {
            return;
        }
        if (cookie.hasExpired()) {
            remove(uri, cookie);
            return;
        }
        evictSameIdentity(cookie);
        cookies.add(cookie);
        if (uri != null) {
            origins.put(cookie, uri);
        }
        createdAt.put(cookie, System.currentTimeMillis());
        saveToFile();
    }

    @Override
    public synchronized List<HttpCookie> get(URI uri) {
        purgeExpired();
        if (uri == null) {
            return Collections.emptyList();
        }
        String host = uri.getHost();
        String path = uri.getPath();
        boolean secureProtocol = "https".equalsIgnoreCase(uri.getScheme());
        List<HttpCookie> result = new ArrayList<>();
        for (HttpCookie cookie : cookies) {
            if (!domainMatches(cookie, host)) {
                continue;
            }
            if (!pathMatches(cookie.getPath(), path)) {
                continue;
            }
            if (cookie.getSecure() && !secureProtocol) {
                continue;
            }
            result.add(cookie);
        }
        return result;
    }

    @Override
    public synchronized List<HttpCookie> getCookies() {
        purgeExpired();
        return new ArrayList<>(cookies);
    }

    @Override
    public synchronized List<URI> getURIs() {
        purgeExpired();
        return origins.values().stream().distinct().toList();
    }

    @Override
    public synchronized boolean remove(URI uri, HttpCookie cookie) {
        boolean removed = cookies.remove(cookie);
        origins.remove(cookie);
        createdAt.remove(cookie);
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    @Override
    public synchronized boolean removeAll() {
        boolean hadCookies = !cookies.isEmpty();
        cookies.clear();
        origins.clear();
        createdAt.clear();
        saveToFile(); // persist the empty state so the logout survives restarts too
        return hadCookies;
    }

    // --- Matching (simplified RFC 6265) ---

    private boolean domainMatches(HttpCookie cookie, String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String domain = cookie.getDomain();
        if (domain == null || domain.isBlank()) {
            // Host-only cookie: only send back to the origin host.
            URI origin = origins.get(cookie);
            return origin == null || origin.getHost() == null
                    || host.equalsIgnoreCase(origin.getHost());
        }
        String normalized = domain.startsWith(".") ? domain.substring(1) : domain;
        String lowerHost = host.toLowerCase();
        String lowerDomain = normalized.toLowerCase();
        return lowerHost.equals(lowerDomain) || lowerHost.endsWith("." + lowerDomain);
    }

    private static boolean pathMatches(String cookiePath, String requestPath) {
        if (cookiePath == null || cookiePath.isBlank()) {
            return true;
        }
        if (requestPath == null || requestPath.isBlank()) {
            requestPath = "/";
        }
        return requestPath.startsWith(cookiePath);
    }

    private void evictSameIdentity(HttpCookie cookie) {
        List<HttpCookie> evicted = new ArrayList<>();
        for (HttpCookie existing : cookies) {
            if (sameIdentity(existing, cookie)) {
                evicted.add(existing);
            }
        }
        for (HttpCookie old : evicted) {
            cookies.remove(old);
            origins.remove(old);
            createdAt.remove(old);
        }
    }

    private static boolean sameIdentity(HttpCookie a, HttpCookie b) {
        return a.getName().equalsIgnoreCase(b.getName())
                && domainKey(a).equalsIgnoreCase(domainKey(b))
                && pathKey(a).equals(pathKey(b));
    }

    private static String domainKey(HttpCookie cookie) {
        return cookie.getDomain() == null ? "" : cookie.getDomain();
    }

    private static String pathKey(HttpCookie cookie) {
        return cookie.getPath() == null ? "/" : cookie.getPath();
    }

    private void purgeExpired() {
        List<HttpCookie> expired = new ArrayList<>();
        for (HttpCookie cookie : cookies) {
            if (cookie.hasExpired()) {
                expired.add(cookie);
            }
        }
        for (HttpCookie cookie : expired) {
            cookies.remove(cookie);
            origins.remove(cookie);
            createdAt.remove(cookie);
        }
        if (!expired.isEmpty()) {
            saveToFile();
        }
    }

    // --- File persistence (small binary format; HttpCookie is not Serializable) ---

    private void saveToFile() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            // Each cookie is serialized independently so one oversized cookie
            // can be skipped without losing the whole store.
            List<byte[]> blobs = new ArrayList<>();
            for (HttpCookie cookie : cookies) {
                byte[] blob = trySerialize(cookie);
                if (blob != null) {
                    blobs.add(blob);
                }
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
                out.writeInt(FILE_VERSION);
                out.writeInt(blobs.size());
                for (byte[] blob : blobs) {
                    out.writeInt(blob.length);
                    out.write(blob);
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Arena] Could not save cookies to " + file + ": " + e.getMessage());
        }
    }

    private byte[] trySerialize(HttpCookie cookie) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeCookie(out, cookie);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            System.err.println("[Arena] Skipping cookie that cannot be saved ("
                    + cookie.getName() + "): " + e.getMessage());
            return null;
        }
    }

    private void writeCookie(DataOutputStream out, HttpCookie cookie) throws IOException {
        writeNullableUTF(out, cookie.getName());
        writeNullableUTF(out, cookie.getValue());
        writeNullableUTF(out, cookie.getComment());
        writeNullableUTF(out, cookie.getCommentURL());
        writeNullableUTF(out, cookie.getDomain());
        writeNullableUTF(out, cookie.getPath());
        writeNullableUTF(out, cookie.getPortlist());
        URI origin = origins.get(cookie);
        writeNullableUTF(out, origin == null ? null : origin.toString());
        out.writeLong(cookie.getMaxAge());
        out.writeLong(createdAt.getOrDefault(cookie, System.currentTimeMillis()));
        out.writeBoolean(cookie.getSecure());
        out.writeBoolean(cookie.isHttpOnly());
        out.writeBoolean(cookie.getDiscard());
        out.writeInt(cookie.getVersion());
    }

    private void loadFromFile() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != FILE_VERSION) {
                System.err.println("[Arena] Ignoring cookies file with unknown version: " + file);
                return;
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int length = in.readInt();
                byte[] blob = in.readNBytes(length);
                try {
                    readCookie(new DataInputStream(new ByteArrayInputStream(blob)));
                } catch (IOException e) {
                    System.err.println("[Arena] Skipping one unreadable cookie: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[Arena] Could not load cookies from " + file + " (starting fresh): " + e.getMessage());
            cookies.clear();
            origins.clear();
            createdAt.clear();
        }
    }

    private void readCookie(DataInputStream in) throws IOException {
        String name = readNullableUTF(in);
        String value = readNullableUTF(in);
        if (name == null) {
            return;
        }
        HttpCookie cookie = new HttpCookie(name, value == null ? "" : value);
        cookie.setComment(readNullableUTF(in));
        cookie.setCommentURL(readNullableUTF(in));
        cookie.setDomain(readNullableUTF(in));
        cookie.setPath(readNullableUTF(in));
        cookie.setPortlist(readNullableUTF(in));
        String origin = readNullableUTF(in);
        long maxAge = in.readLong();
        long created = in.readLong();
        cookie.setSecure(in.readBoolean());
        cookie.setHttpOnly(in.readBoolean());
        cookie.setDiscard(in.readBoolean());
        cookie.setVersion(in.readInt());

        if (maxAge >= 0) {
            // Age the cookie by the time spent away from the app.
            long remaining = maxAge - (System.currentTimeMillis() - created) / 1000;
            if (remaining <= 0) {
                return; // expired while the app was closed
            }
            cookie.setMaxAge(remaining);
        } else {
            cookie.setMaxAge(-1); // session cookie: kept, the server decides if still valid
        }
        if (cookie.hasExpired()) {
            return;
        }
        cookies.add(cookie);
        if (origin != null) {
            try {
                origins.put(cookie, new URI(origin));
            } catch (Exception ignored) {
                // Origin is best-effort only.
            }
        }
        createdAt.put(cookie, created);
    }

    private static void writeNullableUTF(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableUTF(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }
}
