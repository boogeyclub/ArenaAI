package cm.odigital.arenaai;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Central logging for the Arena desktop app: console + rotating log files.
 *
 * <p>Log files live under {@code %APPDATA%\Arena\logs} ({@code arena-0.log} is
 * the current one). Call {@link #setup()} once from {@link Launcher#main}
 * before anything else; all other methods are safe to call anytime.</p>
 */
public final class AppLog {

    private static final Logger LOG = Logger.getLogger("cm.odigital.arenaai");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static volatile boolean installed = false;
    private static Path logDir;
    private static Path logFile;

    private AppLog() {
        // Utility class — no instances.
    }

    /** Installs console + file handlers and the uncaught-exception hook. Idempotent. */
    public static synchronized void setup() {
        if (installed) {
            return;
        }
        installed = true;
        LOG.setUseParentHandlers(false);
        LOG.setLevel(Level.ALL);
        for (Handler handler : LOG.getHandlers()) {
            LOG.removeHandler(handler);
        }

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(Level.INFO);
        console.setFormatter(new LogFormatter());
        LOG.addHandler(console);

        try {
            logDir = AppPaths.appDataDir().resolve("logs");
            Files.createDirectories(logDir);
            FileHandler file = new FileHandler(logDir.resolve("arena-%g.log").toString(), 1024 * 1024, 5, true);
            file.setLevel(Level.ALL);
            file.setFormatter(new LogFormatter());
            LOG.addHandler(file);
            logFile = logDir.resolve("arena-0.log");
        } catch (IOException e) {
            logDir = null;
            logFile = null;
            LOG.log(Level.WARNING, "File logging disabled (cannot create log dir): " + e.getMessage());
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                LOG.log(Level.SEVERE, "Uncaught exception on thread " + thread.getName(), error));

        info("Logging initialized" + (logFile != null ? " (" + logFile + ")" : " (console only)"));
    }

    /** Current log file, or null if file logging is unavailable. */
    public static Path getLogFile() {
        return (logFile != null && Files.isRegularFile(logFile)) ? logFile : null;
    }

    /** Log directory, or null if file logging is unavailable. */
    public static Path getLogDir() {
        return (logDir != null && Files.isDirectory(logDir)) ? logDir : null;
    }

    public static void severe(String message) {
        LOG.severe(message);
    }

    public static void severe(String message, Throwable thrown) {
        if (thrown != null) {
            LOG.log(Level.SEVERE, message, thrown);
        } else {
            LOG.severe(message);
        }
    }

    public static void warning(String message) {
        LOG.warning(message);
    }

    public static void warning(String message, Throwable thrown) {
        if (thrown != null) {
            LOG.log(Level.WARNING, message, thrown);
        } else {
            LOG.warning(message);
        }
    }

    public static void info(String message) {
        LOG.info(message);
    }

    public static void fine(String message) {
        LOG.fine(message);
    }

    /** One line per record: {@code [timestamp] [LEVEL] [thread] message} + stack trace. */
    private static final class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(256);
            sb.append('[').append(LocalDateTime.now().format(TS)).append("] ");
            sb.append('[').append(record.getLevel().getName()).append("] ");
            sb.append('[').append(Thread.currentThread().getName()).append("] ");
            sb.append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter stack = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(stack));
                sb.append(stack);
            }
            return sb.toString();
        }
    }
}
