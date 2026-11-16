package io.github.f4iyy.ubc780xlt;

import io.github.f4iyy.ubc780xlt.ui.Theme;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** User settings persisted in {@code config.properties}. */
public final class AppConfig {

    public static final int AUTO_BAUD = 0;

    private final Path file;
    private String port = "";
    private int baud = AUTO_BAUD;
    private int pollingMs = 250;
    private int reconnectSeconds = 3;
    private Theme theme = Theme.MOCHA;
    private String language = "en";
    private boolean history = true;
    private boolean remotePanel = true;
    private int historyPollMs = 100;

    public AppConfig(Path file) {
        this.file = file;
    }

    /** Loads the file; a missing or unreadable file gives the defaults. */
    public static AppConfig load(Path file) {
        AppConfig config = new AppConfig(file);
        if (!Files.isRegularFile(file)) {
            return config;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return config;
        }
        config.port = p.getProperty("port", "");
        config.baud = parseInt(p.getProperty("baud"), AUTO_BAUD, 0, 115200);
        config.pollingMs = parseInt(p.getProperty("pollingMs"), 250, 100, 2000);
        config.reconnectSeconds = parseInt(p.getProperty("reconnectSeconds"), 3, 1, 60);
        config.theme = Theme.fromId(p.getProperty("theme"));
        config.language = "fr".equals(p.getProperty("language")) ? "fr" : "en";
        config.history = !"false".equals(p.getProperty("history"));
        config.remotePanel = !"false".equals(p.getProperty("remotePanel"));
        config.historyPollMs = parseInt(p.getProperty("historyPollMs"), 100, 50, 1000);
        return config;
    }

    public void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("port", port);
        p.setProperty("baud", Integer.toString(baud));
        p.setProperty("pollingMs", Integer.toString(pollingMs));
        p.setProperty("reconnectSeconds", Integer.toString(reconnectSeconds));
        p.setProperty("theme", theme.id());
        p.setProperty("language", language);
        p.setProperty("history", Boolean.toString(history));
        p.setProperty("remotePanel", Boolean.toString(remotePanel));
        p.setProperty("historyPollMs", Integer.toString(historyPollMs));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "UBC780XLT Companion");
        }
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        if (value == null) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(value.trim());
            return v < min || v > max ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String port() {
        return port;
    }

    public void setPort(String port) {
        this.port = port == null ? "" : port;
    }

    /** Serial speed, or {@link #AUTO_BAUD} for auto-detection. */
    public int baud() {
        return baud;
    }

    public void setBaud(int baud) {
        this.baud = baud;
    }

    public int pollingMs() {
        return pollingMs;
    }

    public void setPollingMs(int pollingMs) {
        this.pollingMs = Math.max(100, Math.min(2000, pollingMs));
    }

    public int reconnectSeconds() {
        return reconnectSeconds;
    }

    public Theme theme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    /** Whether the remote panel (display, keypad, tune) is shown above the tabs. */
    public boolean remotePanel() {
        return remotePanel;
    }

    public void setRemotePanel(boolean remotePanel) {
        this.remotePanel = remotePanel;
    }

    /** Whether receptions are recorded in the history. */
    public boolean history() {
        return history;
    }

    public void setHistory(boolean history) {
        this.history = history;
    }

    /** How often the squelch is looked at, 50 to 1000 ms. */
    public int historyPollMs() {
        return historyPollMs;
    }

    public String language() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = "fr".equals(language) ? "fr" : "en";
    }
}
