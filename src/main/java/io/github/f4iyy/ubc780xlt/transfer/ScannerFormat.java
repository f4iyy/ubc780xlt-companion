package io.github.f4iyy.ubc780xlt.transfer;

import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats commands and parses answers of the memory commands ({@code PM}, {@code TA}, {@code LL}...). */
public final class ScannerFormat {

    /** Answer of {@code PM}/{@code MA}: {@code C015 F04060125 TF DN LF AF RF N000}. */
    public record ChannelLine(
            int channel,
            BigDecimal mhz,
            boolean trunk,
            boolean delayOn,
            boolean lockout,
            boolean att,
            boolean rec,
            Tone tone) {
    }

    private static final Pattern CHANNEL_LINE = Pattern.compile(
            "C(\\d{3}) F(\\d{8}) T([NF]) D([NF]) L([NF]) A([NF])(?: R([NF]))? N(\\d{3})(L?)");
    private static final Pattern LIMIT = Pattern.compile("L[LU](\\d{8})(?: [A-J])?");
    private static final Pattern PRIORITY = Pattern.compile("PC ?([A-J])(\\d{3})");

    private ScannerFormat() {
    }

    /** Bank letter: 1 gives {@code A}, 10 gives {@code J}. */
    public static String letter(int bank) {
        return String.valueOf((char) ('A' + bank - 1));
    }

    /** The 8 digits of a frequency in hundreds of hertz, as a number of MHz. */
    public static BigDecimal mhz(String digits8) {
        return new BigDecimal(digits8).movePointLeft(4);
    }

    public static Optional<ChannelLine> parseChannel(String answer) {
        Matcher m = CHANNEL_LINE.matcher(answer.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        int code = Integer.parseInt(m.group(8));
        if (code > io.github.f4iyy.ubc780xlt.model.ToneTable.MAX_CODE) {
            return Optional.empty();
        }
        return Optional.of(new ChannelLine(
                Integer.parseInt(m.group(1)),
                mhz(m.group(2)),
                on(m.group(3)),
                on(m.group(4)),
                on(m.group(5)),
                on(m.group(6)),
                m.group(7) != null && on(m.group(7)),
                new Tone(code, !m.group(9).isEmpty())));
    }

    private static boolean on(String flag) {
        return "N".equals(flag);
    }

    /** The tag in {@code "<prefix>TEXT"}, with trailing blanks removed; empty if the answer has another shape. */
    public static Optional<String> parseTag(String answer, String prefix) {
        String a = answer.stripTrailing();
        if (a.equals(prefix.stripTrailing())) {
            return Optional.of("");
        }
        return a.startsWith(prefix) ? Optional.of(a.substring(prefix.length())) : Optional.empty();
    }

    /** Frequency of {@code LL}/{@code LU}: {@code LL01508150 A} gives 150.8150. */
    public static Optional<BigDecimal> parseLimit(String answer) {
        Matcher m = LIMIT.matcher(answer.strip());
        return m.matches() ? Optional.of(mhz(m.group(1))) : Optional.empty();
    }

    /** {@code BAN C 001} is on, {@code BAF C 001} is off. */
    public static Optional<Boolean> parseOnOff(String answer, String name) {
        String a = answer.strip();
        if (a.startsWith(name + "N")) {
            return Optional.of(true);
        }
        if (a.startsWith(name + "F")) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    /** {@code DLF} is no delay; {@code DL +2} (or {@code DLN +2}) is a delay. */
    public static Optional<Delay> parseDelay(String answer) {
        String a = answer.strip();
        if (a.equals("DLF")) {
            return Optional.of(Delay.NONE);
        }
        if (a.startsWith("DLN")) {
            a = a.substring(3);
        } else if (a.startsWith("DL")) {
            a = a.substring(2);
        } else {
            return Optional.empty();
        }
        return Optional.ofNullable(Delay.parse(a.strip()));
    }

    /** {@code RM AM}; {@code RM ---} (no frequency) gives nothing. */
    public static Optional<Modulation> parseModulation(String answer) {
        String a = answer.strip();
        return a.startsWith("RM ") ? Optional.ofNullable(Modulation.parse(a.substring(3))) : Optional.empty();
    }

    /** {@code ST 12.5K}. */
    public static Optional<Step> parseStep(String answer) {
        String a = answer.strip();
        return a.startsWith("ST ") ? Optional.ofNullable(Step.parse(a.substring(3))) : Optional.empty();
    }

    /**
     * Priority channel of a bank as a channel number 1 to 500. The scanner counts over all 500 channels:
     * a real BC780XLT answers {@code PC B051} for the first channel of bank B. Every bank always has one
     * (by default its first channel).
     */
    public static Optional<Integer> parsePriority(String answer, int bank) {
        Matcher m = PRIORITY.matcher(answer.strip());
        if (!m.matches() || !m.group(1).equals(letter(bank))) {
            return Optional.empty();
        }
        int n = Integer.parseInt(m.group(2));
        int first = (bank - 1) * Programming.CHANNELS_PER_BANK + 1;
        return n >= first && n < first + Programming.CHANNELS_PER_BANK ? Optional.of(n) : Optional.empty();
    }
}
