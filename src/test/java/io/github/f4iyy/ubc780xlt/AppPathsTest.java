package io.github.f4iyy.ubc780xlt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppPathsTest {

    @Test
    void configLivesInTheUserFolder() {
        assertTrue(AppPaths.configDir().endsWith(".ubc780xlt"));
        assertEquals("config.properties", AppPaths.configFile().getFileName().toString());
    }

    @Test
    void logsGoNextToTheWorkingDirectoryWhenWritable(@TempDir Path dir) {
        assertEquals(dir.resolve("logs"), AppPaths.logDir(dir));
    }

    @Test
    void logsFallBackToTheUserFolderWhenNotWritable(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");
        assertEquals(AppPaths.configDir().resolve("logs"), AppPaths.logDir(missing));
    }

    @Test
    void defaultLogDirIsNamedLogs() {
        assertEquals("logs", AppPaths.logDir().getFileName().toString());
    }
}
