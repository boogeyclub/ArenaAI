package cm.odigital.arenaai;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.util.Duration;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controls the Discord-style splash screen: logo, rotating tips,
 * live loading status and a progress bar bound to the page load.
 */
public class SplashController implements Initializable {

    /** Rotating loading tips, Discord-style. */
    private static final String[] QUOTES = {
            "DID YOU KNOW — This desktop app is arena.ai, minus the browser tabs.",
            "TIP — Press F5 or Ctrl+R anywhere to reload the page.",
            "TIP — Alt+Left / Alt+Right moves back and forward.",
            "TIP — Click the address pill to copy the current page link.",
            "DID YOU KNOW — Pop-ups open in your system browser automatically.",
            "TIP — Use the home button to jump back to arena.ai anytime."
    };

    @FXML
    private StackPane root;
    @FXML
    private ImageView logoView;
    @FXML
    private Label quoteLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;

    private Timeline quoteTimeline;
    private int quoteIndex;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadLogo();
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus("Starting Arena…");
        quoteLabel.setText(QUOTES[0]);
        startQuoteRotation();
    }

    /** Binds the splash status + progress bar to the website load worker. */
    public void bindTo(WebEngine engine) {
        Worker<Void> worker = engine.getLoadWorker();
        progressBar.progressProperty().bind(worker.progressProperty());
        worker.stateProperty().addListener((obs, oldState, newState) -> reflectState(newState));
        reflectState(worker.getState());
    }

    private void reflectState(Worker.State state) {
        if (state == null) {
            return;
        }
        switch (state) {
            case READY -> setStatus("Preparing…");
            case SCHEDULED -> setStatus("Connecting to " + ArenaConfig.HOME_HOST + "…");
            case RUNNING -> setStatus("Loading Arena…");
            case SUCCEEDED -> setStatus("Almost there…");
            case FAILED, CANCELLED -> setStatus("Having trouble reaching " + ArenaConfig.HOME_HOST + "…");
        }
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** Entrance fade played right after the splash window opens. */
    public void playShow() {
        root.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(280), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    /** Exit fade; {@code onFinished} runs on the FX thread afterwards. */
    public void playHide(Runnable onFinished) {
        stopQuoteRotation();
        FadeTransition fadeOut = new FadeTransition(Duration.millis(350), root);
        fadeOut.setFromValue(root.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> onFinished.run());
        fadeOut.play();
    }

    private void startQuoteRotation() {
        quoteIndex = 0;
        quoteTimeline = new Timeline(new KeyFrame(Duration.seconds(2.4), e -> showNextQuote()));
        quoteTimeline.setCycleCount(Animation.INDEFINITE);
        quoteTimeline.play();
    }

    private void stopQuoteRotation() {
        if (quoteTimeline != null) {
            quoteTimeline.stop();
            quoteTimeline = null;
        }
    }

    private void showNextQuote() {
        quoteIndex = (quoteIndex + 1) % QUOTES.length;
        String next = QUOTES[quoteIndex];
        FadeTransition fadeOut = new FadeTransition(Duration.millis(160), quoteLabel);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            quoteLabel.setText(next);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(160), quoteLabel);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    /** Loads the logo PNG if present; falls back to the text logo otherwise. */
    private void loadLogo() {
        try (InputStream in = getClass().getResourceAsStream("images/arena-logo.png")) {
            if (in != null) {
                logoView.setImage(new Image(in));
            } else {
                hideLogoView();
            }
        } catch (Exception e) {
            hideLogoView();
        }
    }

    private void hideLogoView() {
        logoView.setVisible(false);
        logoView.setManaged(false);
    }
}
