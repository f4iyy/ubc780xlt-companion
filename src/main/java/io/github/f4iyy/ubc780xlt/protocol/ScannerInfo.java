package io.github.f4iyy.ubc780xlt.protocol;

import java.util.List;
import java.util.Optional;

/** Answer of the {@code SI} command: {@code SI BC780XLT,1000000001,103} (the US model answers {@code UBC780XLT}). */
public record ScannerInfo(String model, String serial, String commandVersion) {

    /** Model names accepted: the documented one and the one the US scanner really reports. */
    public static final List<String> SUPPORTED_MODELS = List.of("BC780XLT", "UBC780XLT");
    private static final String PREFIX = "SI ";

    public static Optional<ScannerInfo> parse(String line) {
        if (line == null || !line.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = line.substring(PREFIX.length()).split(",", -1);
        if (parts.length != 3 || parts[0].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ScannerInfo(parts[0].strip(), parts[1].strip(), parts[2].strip()));
    }

    /** Only the BC780XLT (and its UBC780XLT variant) is supported. */
    public boolean isSupported() {
        return SUPPORTED_MODELS.contains(model);
    }
}
