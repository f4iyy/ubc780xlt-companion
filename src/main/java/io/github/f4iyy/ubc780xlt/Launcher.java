package io.github.f4iyy.ubc780xlt;

import javafx.application.Application;

/**
 * Entry point. Kept separate from {@link MainApp} so that the application can be started
 * from a plain classpath (jpackage) without the JavaFX launcher module checks.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Must be set before log4j initialises.
        if (System.getProperty(AppPaths.LOG_DIR_PROPERTY) == null) {
            System.setProperty(AppPaths.LOG_DIR_PROPERTY, AppPaths.logDir().toString());
        }
        Application.launch(MainApp.class, args);
    }
}
