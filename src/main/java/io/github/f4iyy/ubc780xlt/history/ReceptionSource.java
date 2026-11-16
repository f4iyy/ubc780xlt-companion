package io.github.f4iyy.ubc780xlt.history;

import java.io.IOException;

/** What the tracker needs from the scanner. */
public interface ReceptionSource {

    /** What the display and the signal meter show when a reception starts. */
    record Snapshot(int signal, String frequency, int bank, int channel, String tag, String modulation, String tone) {
    }

    /** True while the squelch is open ({@code SQ} answers {@code +}). */
    boolean squelchOpen() throws IOException;

    /** Everything known about the reception in progress. */
    Snapshot snapshot() throws IOException;

    /** The signal strength now, 0 to 255 ({@code SG}). */
    int signal() throws IOException;
}
