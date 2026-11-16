package io.github.f4iyy.ubc780xlt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.f4iyy.ubc780xlt.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWhenTheFileIsMissing() {
        AppConfig c = AppConfig.load(dir.resolve("none.properties"));
        assertEquals("", c.port());
        assertEquals(AppConfig.AUTO_BAUD, c.baud());
        assertEquals(250, c.pollingMs());
        assertEquals(3, c.reconnectSeconds());
        assertEquals(Theme.MOCHA, c.theme());
        assertEquals("en", c.language());
        assertEquals(true, c.history());
        assertEquals(true, c.remotePanel());
    }

    @Test
    void saveThenLoadKeepsEverything() throws IOException {
        Path file = dir.resolve("sub/config.properties");
        AppConfig c = new AppConfig(file);
        c.setPort("COM7");
        c.setBaud(19200);
        c.setPollingMs(500);
        c.setTheme(Theme.LATTE);
        c.setLanguage("fr");
        c.setHistory(false);
        c.setRemotePanel(false);
        c.save();

        AppConfig loaded = AppConfig.load(file);
        assertEquals("COM7", loaded.port());
        assertEquals(19200, loaded.baud());
        assertEquals(500, loaded.pollingMs());
        assertEquals(Theme.LATTE, loaded.theme());
        assertEquals("fr", loaded.language());
        assertEquals(false, loaded.history());
        assertEquals(false, loaded.remotePanel());
    }

    @Test
    void invalidValuesFallBackToDefaults() throws IOException {
        Path file = dir.resolve("bad.properties");
        Files.writeString(file, """
                port=COM1
                baud=abc
                pollingMs=5
                reconnectSeconds=9999
                theme=neon
                language=de
                """);
        AppConfig c = AppConfig.load(file);
        assertEquals("COM1", c.port());
        assertEquals(AppConfig.AUTO_BAUD, c.baud());
        assertEquals(250, c.pollingMs());
        assertEquals(3, c.reconnectSeconds());
        assertEquals(Theme.MOCHA, c.theme());
        assertEquals("en", c.language());
    }

    @Test
    void unreadableFileGivesDefaults() throws IOException {
        // A directory where the file should be.
        Path asDir = Files.createDirectory(dir.resolve("config.properties"));
        assertEquals("", AppConfig.load(asDir).port());
    }

    @Test
    void settersNormaliseValues() {
        AppConfig c = new AppConfig(dir.resolve("x"));
        c.setPort(null);
        assertEquals("", c.port());
        c.setPollingMs(1);
        assertEquals(100, c.pollingMs());
        c.setPollingMs(99999);
        assertEquals(2000, c.pollingMs());
        c.setLanguage("es");
        assertEquals("en", c.language());
    }

    @Test
    void blankBaudValueUsesTheDefault() throws IOException {
        Path file = dir.resolve("blank.properties");
        Files.writeString(file, "baud=\n");
        assertEquals(AppConfig.AUTO_BAUD, AppConfig.load(file).baud());
    }
}
