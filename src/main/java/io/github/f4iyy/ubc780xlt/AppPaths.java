package io.github.f4iyy.ubc780xlt;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locations of the configuration file and of the logs. */
public final class AppPaths {

    public static final String LOG_DIR_PROPERTY = "ubc780xlt.logDir";

    private AppPaths() {
    }

    /** User configuration folder: {@code ~/.ubc780xlt}. */
    public static Path configDir() {
        return Path.of(System.getProperty("user.home"), ".ubc780xlt");
    }

    public static Path configFile() {
        return configDir().resolve("config.properties");
    }

    /** {@code logs/} in the working directory when writable, otherwise {@code ~/.ubc780xlt/logs}. */
    public static Path logDir() {
        return logDir(Path.of("").toAbsolutePath());
    }

    static Path logDir(Path workingDir) {
        if (Files.isWritable(workingDir)) {
            return workingDir.resolve("logs");
        }
        return configDir().resolve("logs");
    }
}
