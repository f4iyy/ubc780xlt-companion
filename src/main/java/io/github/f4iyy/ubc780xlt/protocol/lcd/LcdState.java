package io.github.f4iyy.ubc780xlt.protocol.lcd;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything shown on the scanner display, as returned by the {@code LCD} command.
 *
 * @param icons state of each icon
 * @param bar trunk activity bar, one symbol per segment ({@code +}, {@code -}, {@code *})
 * @param signal signal meter bars, e.g. {@code +++---}
 * @param banks the 10 bank numbers, {@code +} selected, {@code *} currently scanned
 * @param channel the channel field, e.g. {@code 470} or {@code bn6} (text between the brackets)
 * @param frequency the frequency field, e.g. {@code 430.3500}
 * @param ctc the CTCSS/DCS field, or the trunk ID
 * @param line1 first dot-matrix line (16 characters)
 * @param line1Cursor cursor row under {@code line1}
 * @param line2 second dot-matrix line (16 characters)
 * @param line2Cursor cursor row under {@code line2}
 */
public record LcdState(
        Map<Icon, Indicator> icons,
        String bar,
        String signal,
        String banks,
        String channel,
        String frequency,
        String ctc,
        String line1,
        String line1Cursor,
        String line2,
        String line2Cursor) {

    public static final int BAR_SEGMENTS = 29;
    public static final int SIGNAL_BARS = 6;
    public static final int BANKS = 10;
    public static final int LINE_WIDTH = 16;

    /** A blank display (scanner not connected). */
    public static LcdState blank() {
        return new LcdState(
                new EnumMap<>(Icon.class),
                "-".repeat(BAR_SEGMENTS),
                "-".repeat(SIGNAL_BARS),
                "-".repeat(BANKS),
                "",
                "",
                "",
                " ".repeat(LINE_WIDTH),
                " ".repeat(LINE_WIDTH),
                " ".repeat(LINE_WIDTH),
                " ".repeat(LINE_WIDTH));
    }

    public Indicator icon(Icon icon) {
        return icons.getOrDefault(icon, Indicator.OFF);
    }

    /** Number of lit signal-meter bars (0 to 6). */
    public int signalBars() {
        return (int) signal.chars().filter(c -> c == '+').count();
    }
}
