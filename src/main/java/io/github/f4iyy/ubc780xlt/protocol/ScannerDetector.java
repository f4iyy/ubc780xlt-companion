package io.github.f4iyy.ubc780xlt.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Finds scanners by sending {@code SI} on each port at each speed the scanner supports. */
public final class ScannerDetector {

    /** Speeds supported by the BC780XLT; the factory default (9600) comes first. */
    public static final int[] BAUDS = {9600, 19200, 4800, 2400};

    /** Attempts of {@code SI} on one port and speed when the scanner answers {@code ERR}. */
    private static final int ATTEMPTS = 3;

    private static final int RETRY_PAUSE_MS = 50;

    private static final Logger LOG = LogManager.getLogger(ScannerDetector.class);
    private static final Logger WIRE = LogManager.getLogger("protocol.wire");

    private final LinkFactory factory;
    private final ConnectionSettings settings;

    public ScannerDetector(LinkFactory factory, ConnectionSettings settings) {
        this.factory = factory;
        // A short pause between the attempts of one probe lets the scanner drop garbled input.
        this.settings = new ConnectionSettings(settings.timeoutMs(), RETRY_PAUSE_MS);
    }

    /** Asks {@code SI} on {@code port} at {@code baud}; any model answering in the SI format is returned. */
    public Optional<DetectedScanner> identify(String port, int baud) {
        WIRE.debug("-- open {} at {} baud", port, baud);
        try (SerialLink link = factory.open(port, baud);
                ScannerConnection connection = new ScannerConnection(link, settings)) {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                Response r = connection.send("SI");
                Optional<ScannerInfo> info = ScannerInfo.parse(r.text());
                if (info.isPresent()) {
                    return info.map(i -> new DetectedScanner(port, baud, i));
                }
                if (!r.isError()) {
                    break;
                }
                // ERR/FER/ORER: the scanner is there but received garbage (bytes left over from a probe
                // at another speed stick to the front of the command). Ask again.
                LOG.debug("{} at {} baud answered {} to SI (attempt {})", port, baud, r.text(), attempt);
            }
            return Optional.empty();
        } catch (IOException e) {
            LOG.debug("No scanner on {} at {} baud: {}", port, baud, e.getMessage());
            return Optional.empty();
        }
    }

    /** Tries every supported speed on {@code port}. */
    public Optional<DetectedScanner> probe(String port) {
        for (int baud : BAUDS) {
            Optional<DetectedScanner> found = identify(port, baud);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Probes every available port.
     *
     * @param progress told the name of each port before it is probed
     * @return the BC780XLT scanners found
     */
    public List<DetectedScanner> scanAll(Consumer<String> progress) {
        List<DetectedScanner> found = new ArrayList<>();
        for (String port : factory.listPorts()) {
            progress.accept(port);
            probe(port).filter(DetectedScanner::isSupported).ifPresent(found::add);
        }
        return found;
    }
}
