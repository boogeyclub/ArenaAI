package cm.odigital.arenaai;

import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.PromptData;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controls the main window: a slim desktop browser chrome (back / forward /
 * reload / home + address pill) wrapped around the preloaded Arena page.
 */
public class BrowserController implements Initializable {

    @FXML
    private BorderPane root;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button reloadButton;
    @FXML
    private Button homeButton;
    @FXML
    private Button externalButton;
    @FXML
    private Label addressPill;
    @FXML
    private ProgressBar loadProgress;
    @FXML
    private StackPane webContainer;
    @FXML
    private StackPane errorOverlay;
    @FXML
    private Label errorDetail;
    @FXML
    private Button retryButton;
    @FXML
    private Button openExternalButton;

    private WebView webView;
    private WebEngine engine;
    private WebHistory history;
    private Tooltip addressTooltip;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadProgress.setProgress(0);
        loadProgress.setVisible(false);
        errorOverlay.setVisible(false);
        addressPill.setText(ArenaConfig.HOME_HOST);

        retryButton.setOnAction(e -> {
            hideError();
            if (engine != null) {
                engine.reload();
            }
        });
        openExternalButton.setOnAction(e -> openInSystemBrowser(currentLocation()));
    }

    /**
     * Attaches the WebView that was preloaded behind the splash screen, so the
     * website appears instantly without loading a second time.
     */
    public void setWebView(WebView webView) {
        this.webView = webView;
        this.engine = webView.getEngine();
        this.history = engine.getHistory();

        // Index 0 keeps the error overlay (declared in FXML) on top.
        webContainer.getChildren().add(0, webView);

        addressTooltip = new Tooltip(engine.getLocation());
        addressTooltip.textProperty().bind(engine.locationProperty());
        Tooltip.install(addressPill, addressTooltip);
        addressPill.setOnMouseClicked(e -> copyCurrentUrlToClipboard());

        wireEngine();
        wireToolbar();
        wireShortcuts();
        wireHistoryButtons();

        // The page may already have settled while the splash was showing.
        reflectWorkerState(engine.getLoadWorker().getState());
        updateAddressPill(engine.getLocation());
        updateWindowTitle(engine.getTitle());
    }

    // --- Engine wiring ---

    private void wireEngine() {
        Worker<Void> worker = engine.getLoadWorker();

        loadProgress.progressProperty().bind(worker.progressProperty());
        loadProgress.visibleProperty().bind(worker.runningProperty());

        worker.stateProperty().addListener((obs, oldState, newState) -> reflectWorkerState(newState));
        worker.exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null && worker.getState() == Worker.State.FAILED) {
                showError(messageOf(newEx));
            }
        });

        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> updateAddressPill(newLoc));
        engine.titleProperty().addListener((obs, oldTitle, newTitle) -> updateWindowTitle(newTitle));

        // Links that want a new window (target="_blank", window.open, …) open
        // in the user's system browser instead of a second app window.
        engine.setCreatePopupHandler(features -> {
            WebEngine popupEngine = new WebView().getEngine();
            popupEngine.locationProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends String> obs, String oldLoc, String newLoc) {
                    if (newLoc != null && !newLoc.isBlank() && !"about:blank".equals(newLoc)) {
                        openInSystemBrowser(newLoc);
                        obs.removeListener(this);
                    }
                }
            });
            return popupEngine;
        });

        // JavaScript dialogs → native JavaFX dialogs.
        engine.setOnAlert(event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(ArenaConfig.APP_TITLE);
            alert.setHeaderText("arena.ai says:");
            alert.setContentText(event.getData());
            alert.showAndWait();
        });
        engine.setConfirmHandler(message -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle(ArenaConfig.APP_TITLE);
            alert.setHeaderText("arena.ai asks:");
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        });
        engine.setPromptHandler((PromptData data) -> {
            TextInputDialog dialog = new TextInputDialog(data.getDefaultValue());
            dialog.setTitle(ArenaConfig.APP_TITLE);
            dialog.setHeaderText(data.getMessage());
            return dialog.showAndWait().orElse(null);
        });

        engine.setOnError(event -> System.err.println("[Arena WebView] " + event.getMessage()));
    }

    private void reflectWorkerState(Worker.State state) {
        if (state == null) {
            return;
        }
        switch (state) {
            case RUNNING, SCHEDULED -> hideError();
            case SUCCEEDED -> {
                hideError();
                updateWindowTitle(engine.getTitle());
            }
            case FAILED -> showError(messageOf(engine.getLoadWorker().getException()));
            case CANCELLED -> {
                // User navigated away mid-load; only show the overlay if nothing loaded.
                if (engine.getLocation() == null || engine.getLocation().isBlank()) {
                    showError("Navigation was cancelled before anything loaded.");
                }
            }
            default -> {
                // READY — nothing to do yet.
            }
        }
    }

    private static String messageOf(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "The page could not be loaded. Check your internet connection and retry.";
        }
        return "The page could not be loaded (" + ex.getMessage() + "). Check your internet connection and retry.";
    }

    // --- Toolbar / navigation ---

    private void wireToolbar() {
        backButton.setOnAction(e -> go(-1));
        forwardButton.setOnAction(e -> go(1));
        reloadButton.setOnAction(e -> engine.reload());
        homeButton.setOnAction(e -> engine.load(ArenaConfig.HOME_URL));
        externalButton.setOnAction(e -> openInSystemBrowser(currentLocation()));
    }

    private void wireHistoryButtons() {
        history.currentIndexProperty().addListener((obs, oldIndex, newIndex) -> updateNavButtons());
        history.getEntries().addListener((ListChangeListener<WebHistory.Entry>) c -> updateNavButtons());
        updateNavButtons();
    }

    private void go(int offset) {
        try {
            int target = history.getCurrentIndex() + offset;
            if (target >= 0 && target < history.getEntries().size()) {
                history.go(offset);
            }
        } catch (IndexOutOfBoundsException ignored) {
            // Already at the edge of the history — nothing to do.
        }
    }

    private void updateNavButtons() {
        int index = history.getCurrentIndex();
        int size = history.getEntries().size();
        backButton.setDisable(index <= 0);
        forwardButton.setDisable(index < 0 || index >= size - 1);
    }

    private void wireShortcuts() {
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN), engine::reload);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.F5), engine::reload);
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), () -> go(-1));
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN), () -> go(1));
            newScene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.HOME, KeyCombination.ALT_DOWN),
                    () -> engine.load(ArenaConfig.HOME_URL));
        });
    }

    // --- Address pill / window title ---

    private void updateAddressPill(String location) {
        String host = ArenaConfig.HOME_HOST;
        if (location != null && !location.isBlank()) {
            try {
                String parsed = new URI(location).getHost();
                if (parsed != null && !parsed.isBlank()) {
                    host = parsed;
                }
            } catch (Exception ignored) {
                // Keep the fallback host.
            }
        }
        boolean secure = location != null && location.startsWith("https");
        addressPill.setText((secure ? "🔒 " : "") + host);
    }

    private void copyCurrentUrlToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(currentLocation());
        Clipboard.getSystemClipboard().setContent(content);

        String original = addressPill.getText();
        addressPill.setText("✓ Link copied");
        PauseTransition restore = new PauseTransition(Duration.seconds(1.2));
        restore.setOnFinished(e -> addressPill.setText(original));
        restore.play();
    }

    private void updateWindowTitle(String pageTitle) {
        String title = (pageTitle == null || pageTitle.isBlank())
                ? ArenaConfig.APP_TITLE
                : pageTitle + " — " + ArenaConfig.APP_TITLE;
        if (root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle(title);
        }
    }

    // --- Error overlay ---

    private void showError(String detail) {
        errorDetail.setText(detail);
        errorOverlay.setVisible(true);
        errorOverlay.toFront();
    }

    private void hideError() {
        errorOverlay.setVisible(false);
    }

    // --- Helpers ---

    private String currentLocation() {
        if (engine != null && engine.getLocation() != null && !engine.getLocation().isBlank()) {
            return engine.getLocation();
        }
        return ArenaConfig.HOME_URL;
    }

    private void openInSystemBrowser(String url) {
        try {
            if (url != null && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ex) {
            System.err.println("[Arena] Could not open system browser: " + ex.getMessage());
        }
        // Fallback: copy the link so the user can paste it into a browser.
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
        } catch (Exception ignored) {
            // Nothing more we can do.
        }
    }
}
