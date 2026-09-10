package cm.odigital.arenaai;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;

/**
 * In-app window for sign-in popups (Google, GitHub, Microsoft, …).
 *
 * <p>Non-modal so the main page keeps running while the user signs in.
 * Cookies are shared JVM-wide ({@link CookiePersistence}), so a login
 * completed here applies to the main window too. The popup page is watched
 * for sign-in errors, which are recorded in {@link AppLog}.</p>
 */
public final class AuthPopupDialog {

    private AuthPopupDialog() {
        // Utility class — no instances.
    }

    /**
     * Shows the given popup WebView in a dedicated sign-in window.
     *
     * @param onClose runs on the FX thread when the window is closed (may be null)
     */
    public static void show(WebView popupView, String url, Window owner, Runnable onClose) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.NONE);
        dialog.setTitle("Sign in — " + ArenaConfig.APP_TITLE);
        try (InputStream icon = AuthPopupDialog.class.getResourceAsStream("images/arena-logo.png")) {
            if (icon != null) {
                dialog.getIcons().add(new Image(icon));
            }
        } catch (Exception ignored) {
            // Icon is optional.
        }

        AuthErrorWatcher.watch(popupView.getEngine(), match ->
                AppLog.severe("Sign-in window reports an error ("
                        + popupView.getEngine().getLocation() + "): " + match));

        Label hint = new Label("Complete your sign-in here, then close this window.");
        hint.setPadding(new Insets(8, 12, 8, 12));
        hint.setStyle("-fx-background-color: #2b2d31; -fx-text-fill: #dbdee1; -fx-font-size: 12px;");
        hint.setMaxWidth(Double.MAX_VALUE);

        BorderPane root = new BorderPane(popupView, hint, null, null, null);
        Scene scene = new Scene(root, 520, 700);
        dialog.setScene(scene);
        dialog.setMinWidth(400);
        dialog.setMinHeight(500);
        dialog.setOnCloseRequest(e -> {
            AppLog.info("Sign-in window closed.");
            if (onClose != null) {
                onClose.run();
            }
        });
        AppLog.info("Sign-in window opened for: " + url);
        if (owner != null) {
            dialog.setX(owner.getX() + Math.max(0, (owner.getWidth() - 520) / 2));
            dialog.setY(owner.getY() + Math.max(0, (owner.getHeight() - 700) / 2));
        } else {
            dialog.centerOnScreen();
        }
        dialog.show();
    }
}
