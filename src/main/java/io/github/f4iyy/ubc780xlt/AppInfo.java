package io.github.f4iyy.ubc780xlt;

/** Application identity and the mandatory Uniden notice. */
public final class AppInfo {

    public static final String NAME = "UBC780XLT Companion";

    private static final String UNKNOWN_VERSION = "dev";

    private AppInfo() {
    }

    /** Implementation version from the jar manifest, or {@code "dev"} when run from classes. */
    public static String version() {
        return versionOf(AppInfo.class.getPackage().getImplementationVersion());
    }

    static String versionOf(String manifestVersion) {
        return manifestVersion == null || manifestVersion.isBlank() ? UNKNOWN_VERSION : manifestVersion;
    }
}
