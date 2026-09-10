package cm.odigital.arenaai;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.nio.file.Path;

/**
 * Installs and manages the file-backed cookie store used by the embedded
 * browser, so logins survive app restarts.
 */
public final class CookiePersistence {

    private CookiePersistence() {
        // Utility class — no instances.
    }

    /** Location of the cookie file: {@code %APPDATA%\Arena\cookies.dat} on Windows. */
    public static Path defaultCookieFile() {
        String appData = System.getenv("APPDATA");
        Path dir;
        if (appData != null && !appData.isBlank()) {
            dir = Path.of(appData, "Arena");
        } else {
            dir = Path.of(System.getProperty("user.home"), ".arena");
        }
        return dir.resolve("cookies.dat");
    }

    /**
     * Installs the persistent cookie handler for the whole JVM (WebView routes
     * its cookies through it). Call once, before creating any WebView.
     */
    public static synchronized void install() {
        CookieHandler current = CookieHandler.getDefault();
        if (current instanceof CookieManager manager
                && manager.getCookieStore() instanceof PersistentCookieStore) {
            return; // already installed
        }
        CookieManager manager = new CookieManager(new PersistentCookieStore(), CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    /**
     * Clears all cookies (and persists the empty state).
     *
     * @return true if there was anything to clear
     */
    public static boolean clearAll() {
        CookieHandler current = CookieHandler.getDefault();
        if (current instanceof CookieManager manager) {
            CookieStore store = manager.getCookieStore();
            if (store != null) {
                return store.removeAll();
            }
        }
        return false;
    }
}
