package cm.odigital.arenaai;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        AppLog.setup();
        AppLog.info("Starting " + ArenaConfig.APP_TITLE + " " + ArenaConfig.APP_VERSION
                + " (Java " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.version") + ")");
        Application.launch(ArenaApplication.class, args);
    }
}
