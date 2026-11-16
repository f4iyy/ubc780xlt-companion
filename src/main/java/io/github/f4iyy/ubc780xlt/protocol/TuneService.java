package io.github.f4iyy.ubc780xlt.protocol;

import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Tunes the scanner to a frequency without storing it in memory ({@code RF}), like the "Frequency / Tune"
 * box of ProScan. Sequence: manual mode if needed, step ({@code ST}), frequency ({@code RF}),
 * modulation ({@code RM}, which {@code RF} does not set by itself).
 */
public final class TuneService {

    /** Scanner modes in which {@code RF} is accepted: manual (01) and manual frequency (08). */
    private static final Set<String> MANUAL_MODES = Set.of("MD01", "MD08");

    private final ConnectionManager manager;

    public TuneService(ConnectionManager manager) {
        this.manager = manager;
    }

    /**
     * @param mhz frequency in MHz, must be inside a receivable band
     * @throws IllegalArgumentException if the frequency is outside every band
     * @throws IOException if the scanner refuses a step of the sequence
     */
    public void tune(BigDecimal mhz, Step step, Modulation modulation) throws IOException {
        if (!FrequencyRanges.isValid(mhz)) {
            throw new IllegalArgumentException("Frequency out of range: " + mhz);
        }
        String mode = manager.send("MD").text();
        if (!MANUAL_MODES.contains(mode)) {
            manager.pressKey(ScannerKey.MAN, false);
        }
        expectOk("ST " + step.command());
        expectOk("RF" + FrequencyRanges.toScannerFormat(mhz));
        expectOk("RM " + modulation.name());
    }

    private void expectOk(String command) throws IOException {
        Response r = manager.send(command);
        if (!r.isOk()) {
            throw new IOException(command + " refused: " + r.text());
        }
    }
}
