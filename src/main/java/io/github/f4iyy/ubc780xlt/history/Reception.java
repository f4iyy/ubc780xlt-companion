package io.github.f4iyy.ubc780xlt.history;

import java.time.Duration;
import java.time.Instant;

/**
 * One reception: the squelch opened and later closed.
 *
 * @param start when the squelch opened
 * @param duration how long it stayed open (zero while {@code inProgress})
 * @param bank scan bank 1 to 10, 0 if unknown (e.g. searching)
 * @param channel channel 1 to 500, 0 if unknown
 * @param frequency in MHz, e.g. {@code 145.0150}
 * @param tag alpha tag shown on the scanner display, or empty
 * @param modulation {@code AM}, {@code FM}, {@code NFM}, {@code WFM} or empty
 * @param tone CTCSS/DCS shown on the display, or empty
 * @param maxSignal strongest signal seen (0 to 255, the scanner's {@code SG} value)
 * @param inProgress true until the squelch closes
 */
public record Reception(
        Instant start,
        Duration duration,
        int bank,
        int channel,
        String frequency,
        String tag,
        String modulation,
        String tone,
        int maxSignal,
        boolean inProgress) {

    /** The same reception, finished. */
    public Reception finished(Duration total, int strongest) {
        return new Reception(start, total, bank, channel, frequency, tag, modulation, tone,
                Math.max(maxSignal, strongest), false);
    }

    public Reception withMaxSignal(int signal) {
        return new Reception(start, duration, bank, channel, frequency, tag, modulation, tone,
                Math.max(maxSignal, signal), inProgress);
    }
}
