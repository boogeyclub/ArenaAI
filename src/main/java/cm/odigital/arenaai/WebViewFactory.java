package cm.odigital.arenaai;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Creates pre-configured {@link WebView} instances for the Arena desktop app.
 */
public final class WebViewFactory {

    private WebViewFactory() {
        // Utility class — no instances.
    }

    /**
     * Creates a WebView tuned for desktop browsing: JavaScript enabled,
     * desktop Chrome-on-Windows user agent, default context menu kept.
     *
     * <p>Must be called on the JavaFX Application Thread.</p>
     */
    public static WebView createConfiguredWebView() {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent(ArenaConfig.USER_AGENT);
        webView.setContextMenuEnabled(true);
        webView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return webView;
    }
}
