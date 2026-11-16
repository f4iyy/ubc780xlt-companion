package io.github.f4iyy.ubc780xlt.history;

import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.protocol.MultiLineSender;
import io.github.f4iyy.ubc780xlt.protocol.lcd.Icon;
import io.github.f4iyy.ubc780xlt.protocol.lcd.Indicator;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdParser;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdState;
import io.github.f4iyy.ubc780xlt.transfer.ScannerFormat;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@link ReceptionSource} on a real (or simulated) scanner: {@code SQ}, {@code SG} and {@code LCD}. */
public final class ScannerSource implements ReceptionSource {

    /** Answer of {@code SG}: {@code S107 F01450150}. */
    private static final Pattern SIGNAL = Pattern.compile("S(\\d{3}) F(\\d{8})");

    private final MultiLineSender scanner;

    public ScannerSource(MultiLineSender scanner) {
        this.scanner = scanner;
    }

    @Override
    public boolean squelchOpen() throws IOException {
        return scanner.send("SQ").text().equals("+");
    }

    @Override
    public int signal() throws IOException {
        Matcher m = SIGNAL.matcher(scanner.send("SG").text());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    @Override
    public Snapshot snapshot() throws IOException {
        Matcher m = SIGNAL.matcher(scanner.send("SG").text());
        int signal = 0;
        String frequency = "";
        if (m.matches()) {
            signal = Integer.parseInt(m.group(1));
            frequency = ScannerFormat.mhz(m.group(2)).toPlainString();
        }
        LcdState lcd = LcdParser.parse(scanner.sendLines("LCD", LcdParser.LINE_COUNT));
        if (frequency.isEmpty()) {
            frequency = lcd.frequency().strip();
        }
        int channel = channelOf(lcd);
        return new Snapshot(signal, frequency, bankOf(lcd, channel), channel, tagOf(lcd), modulationOf(lcd),
                toneOf(lcd));
    }

    // ---- reading the display ----------------------------------------------------------------

    /** Channel number from the {@code CHN} field ({@code 188}); 0 when it shows something else. */
    static int channelOf(LcdState lcd) {
        String t = lcd.channel().strip();
        if (t.matches("\\d{1,3}")) {
            int n = Integer.parseInt(t);
            return n >= 1 && n <= Programming.CHANNELS ? n : 0;
        }
        return 0;
    }

    /** The bank of the channel, else the bank the scanner flashes in the bank row. */
    static int bankOf(LcdState lcd, int channel) {
        if (channel > 0) {
            return (channel - 1) / Programming.CHANNELS_PER_BANK + 1;
        }
        int flashing = lcd.banks().indexOf('*');
        return flashing >= 0 ? flashing + 1 : 0;
    }

    /**
     * The alpha tag of the channel: the first line of the display. During a reception on a real scanner the first line
     * shows the channel tag ({@code LFMN ATIS FR}) and the second one the name of the scan bank ({@code AERO}); when
     * scanning the first line is empty and the second one is a prompt ({@code > SCANNING}).
     */
    static String tagOf(LcdState lcd) {
        String first = lcd.line1().strip();
        return first.startsWith(">") ? "" : first;
    }

    static String modulationOf(LcdState lcd) {
        if (lcd.icon(Icon.WFM) != Indicator.OFF) {
            return "WFM";
        }
        if (lcd.icon(Icon.FM) != Indicator.OFF) {
            return lcd.icon(Icon.N) != Indicator.OFF ? "NFM" : "FM";
        }
        return lcd.icon(Icon.AM) != Indicator.OFF ? "AM" : "";
    }

    static String toneOf(LcdState lcd) {
        boolean shown = lcd.icon(Icon.CTCSS) != Indicator.OFF || lcd.icon(Icon.DCS) != Indicator.OFF;
        return shown ? lcd.ctc().strip() : "";
    }
}
