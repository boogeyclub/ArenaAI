package cm.odigital.arenaai;

import java.nio.file.Path;

/** Resolves per-user data directories (Windows %APPDATA% with fallback). */
public final class AppPaths {

    private AppPaths() {
        // Utility class — no instances.
    }

    /**
     * Base directory for app data: {@code %APPDATA%\Arena} on Windows,
     * {@code ~/.arena} elsewhere.
     */
    public static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "Arena");
        }
        return Path.of(System.getProperty("user.home"), ".arena");
    }
}
