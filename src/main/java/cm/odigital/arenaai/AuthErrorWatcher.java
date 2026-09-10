package cm.odigital.arenaai;

import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Scans loaded pages for known sign-in failure signatures (e.g.
 * {@code Auth session missing}, {@code disallowed_useragent}) and reports
 * matches. This turns "invisible" website login errors into visible,
 * actionable diagnostics.
 *
 * <p>All callbacks run on the FX thread. Each URL is reported at most once.</p>
 */
public final class AuthErrorWatcher {

    /** Lowercase page-text signatures that indicate a failed sign-in. */
    private static final String[] SIGNATURES = {
            "auth session missing",
            "disallowed_useragent",
            "access_denied",
            "redirect_uri_mismatch",
            "invalid_client"
    };

    private AuthErrorWatcher() {
        // Utility class — no instances.
    }

    /**
     * Watches the given engine: checks after every page load, plus once more
     * a few seconds later (SPAs often render errors asynchronously).
     *
     * @param onMatch called with a match description (once per URL)
     */
    public static void watch(WebEngine engine, Consumer<String> onMatch) {
        AtomicInteger token = new AtomicInteger(0);
        Set<String> reported = new HashSet<>();
        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> token.incrementAndGet());
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                int current = token.get();
                check(engine, reported, onMatch);
                PauseTransition settle = new PauseTransition(Duration.seconds(2.5));
                settle.setOnFinished(e -> {
                    if (current == token.get()) {
                        check(engine, reported, onMatch);
                    }
                });
                settle.play();
            }
        });
    }

    /**
     * One-shot scan of the currently loaded page.
     *
     * @return match description, or null when no signature is found
     */
    public static String detect(WebEngine engine) {
        try {
            Object result = engine.executeScript(
                    "(function(){ try { return document.documentElement"
                            + " ? document.documentElement.innerText : ''; }"
                            + " catch (e) { return ''; } })();");
            if (!(result instanceof String text) || text.isBlank()) {
                return null;
            }
            String lower = text.toLowerCase();
            for (String signature : SIGNATURES) {
                int index = lower.indexOf(signature);
                if (index >= 0) {
                    return "Matched \"" + signature + "\" — page excerpt: " + excerpt(text, index);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void check(WebEngine engine, Set<String> reported, Consumer<String> onMatch) {
        String url = engine.getLocation();
        if (url == null || url.isBlank() || reported.contains(url)) {
            return;
        }
        String match = detect(engine);
        if (match != null) {
            reported.add(url);
            onMatch.accept(match);
        }
    }

    private static String excerpt(String text, int index) {
        int start = Math.max(0, index - 60);
        int end = Math.min(text.length(), index + 120);
        return "…" + text.substring(start, end).replaceAll("\\s+", " ").trim() + "…";
    }
}
