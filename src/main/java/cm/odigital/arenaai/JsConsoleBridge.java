package cm.odigital.arenaai;

import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

/**
 * Captures the page's JavaScript console (console.log/warn/error, uncaught
 * exceptions, unhandled promise rejections) into {@link AppLog}.
 *
 * <p>This is what makes "invisible" website errors — like a failing Google
 * sign-in — visible in the log file. The hook is re-installed after every
 * page load because each navigation gets a fresh JS context.</p>
 */
public final class JsConsoleBridge {

    private final WebEngine engine;

    private JsConsoleBridge(WebEngine engine) {
        this.engine = engine;
    }

    /** Installs the console hook on the given engine (present and future page loads). */
    public static void install(WebEngine engine) {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                inject(engine);
            }
        });
        if (engine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
            inject(engine);
        }
    }

    private static void inject(WebEngine engine) {
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("arenaJsLog", new JsConsoleBridge(engine));
            engine.executeScript(
                    "(function(){"
                            + "var bridge = window.arenaJsLog;"
                            + "if (!bridge || window.__arenaConsoleHooked) return;"
                            + "window.__arenaConsoleHooked = true;"
                            + "['log','info','warn','error','debug'].forEach(function(level){"
                            + "  var original = null;"
                            + "  try { original = console[level] ? console[level].bind(console) : null; } catch (e) {}"
                            + "  console[level] = function(){"
                            + "    try {"
                            + "      var msg = Array.prototype.map.call(arguments, function(a){"
                            + "        try { return (typeof a === 'object' && a !== null) ? JSON.stringify(a) : String(a); }"
                            + "        catch (e) { try { return String(a); } catch (e2) { return '?'; } }"
                            + "      }).join(' ');"
                            + "      bridge.push(level, msg);"
                            + "    } catch (e) {}"
                            + "    if (original) { try { original.apply(null, arguments); } catch (e2) {} }"
                            + "  };"
                            + "});"
                            + "window.addEventListener('error', function(e){"
                            + "  try { bridge.push('error', 'Uncaught: ' + e.message + ' @ ' + e.filename + ':' + e.lineno); }"
                            + "  catch (x) {}"
                            + "});"
                            + "window.addEventListener('unhandledrejection', function(e){"
                            + "  try {"
                            + "    var r = e.reason;"
                            + "    bridge.push('error', 'Unhandled rejection: ' + (r && r.stack ? r.stack : r));"
                            + "  } catch (x) {}"
                            + "});"
                            + "})();");
            AppLog.fine("JS console bridge installed for: " + engine.getLocation());
        } catch (Exception e) {
            AppLog.warning("Could not install JS console bridge for " + safeLocation(engine), e);
        }
    }

    /**
     * Called from page JavaScript. Must stay public.
     *
     * @param level one of log/info/warn/error/debug
     * @param message the console message
     */
    public void push(String level, String message) {
        String line = "[JS " + level + "] " + message + " (" + safeLocation(engine) + ")";
        switch (level == null ? "" : level) {
            case "error" -> AppLog.severe(line);
            case "warn" -> AppLog.warning(line);
            default -> AppLog.info(line);
        }
    }

    private static String safeLocation(WebEngine engine) {
        try {
            String location = engine.getLocation();
            return location == null ? "?" : location;
        } catch (Exception e) {
            return "?";
        }
    }
}
