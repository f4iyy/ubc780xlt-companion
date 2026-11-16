package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;

/** The scanner did not answer in time. */
public class ScannerTimeoutException extends IOException {

    private static final long serialVersionUID = 1L;

    public ScannerTimeoutException(String command, int timeoutMs) {
        super("No answer to '" + command + "' within " + timeoutMs + " ms");
    }
}
