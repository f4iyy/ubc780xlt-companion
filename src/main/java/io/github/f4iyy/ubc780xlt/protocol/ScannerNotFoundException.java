package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** No BC780XLT answered on the given port at any of the speeds tried. */
public class ScannerNotFoundException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String speeds;

    public ScannerNotFoundException(String port, int... bauds) {
        this(port, Arrays.stream(bauds).mapToObj(Integer::toString).collect(Collectors.joining(", ")));
    }

    private ScannerNotFoundException(String port, String speeds) {
        super("No BC780XLT found on " + port + " (tried " + speeds + " baud)");
        this.speeds = speeds;
    }

    /** The speeds that were tried, e.g. {@code "9600, 19200, 4800, 2400"}. */
    public String speeds() {
        return speeds;
    }
}
