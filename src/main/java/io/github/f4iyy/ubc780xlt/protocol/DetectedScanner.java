package io.github.f4iyy.ubc780xlt.protocol;

/** A scanner found on a port, with the speed it answered at. */
public record DetectedScanner(String port, int baud, ScannerInfo info) {

    public boolean isSupported() {
        return info.isSupported();
    }
}
