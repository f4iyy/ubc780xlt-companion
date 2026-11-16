package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;

/** A device answered, but it is not a BC780XLT. */
public class UnsupportedScannerException extends IOException {

    private static final long serialVersionUID = 1L;

    public UnsupportedScannerException(String model) {
        super("Unsupported scanner model: " + model);
    }
}
