package io.github.f4iyy.ubc780xlt.protocol.lcd;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the answer of the {@code LCD} command into an {@link LcdState}. The answer is 41 lines:
 * the 33 icons ({@code NAME +|-|*}), then {@code BAR}, {@code SMT}, {@code BNK}, {@code CHN},
 * {@code FRQ}, {@code CTC}, {@code LINE1} and {@code LINE2}. Unknown or missing lines are tolerated.
 */
public final class LcdParser {

    /** Number of lines of a complete {@code LCD} answer. */
    public static final int LINE_COUNT = Icon.values().length + 8;

    private static final Pattern FIELD = Pattern.compile("^(\\w+) \\[(.*)]$");
    private static final Pattern TEXT_LINE = Pattern.compile("^LINE[12] \\[(.*?)]\\[(.*?)]$");

    private LcdParser() {
    }

    public static LcdState parse(List<String> lines) {
        Map<Icon, Indicator> icons = new EnumMap<>(Icon.class);
        LcdState blank = LcdState.blank();
        String bar = blank.bar();
        String signal = blank.signal();
        String banks = blank.banks();
        String channel = "";
        String frequency = "";
        String ctc = "";
        String line1 = blank.line1();
        String cursor1 = blank.line1Cursor();
        String line2 = blank.line2();
        String cursor2 = blank.line2Cursor();

        for (String raw : lines) {
            String line = raw.stripTrailing();
            int space = line.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String name = line.substring(0, space);
            String value = line.substring(space + 1);
            switch (name) {
                case "BAR" -> bar = value.strip();
                case "SMT" -> signal = value.strip();
                case "BNK" -> banks = value.strip();
                case "CHN" -> channel = bracketed(line);
                case "FRQ" -> frequency = bracketed(line);
                case "CTC" -> ctc = bracketed(line);
                case "LINE1" -> {
                    Matcher m = TEXT_LINE.matcher(line);
                    if (m.matches()) {
                        line1 = pad(m.group(1));
                        cursor1 = pad(m.group(2));
                    }
                }
                case "LINE2" -> {
                    Matcher m = TEXT_LINE.matcher(line);
                    if (m.matches()) {
                        line2 = pad(m.group(1));
                        cursor2 = pad(m.group(2));
                    }
                }
                default -> iconOf(name).ifPresent(icon -> icons.put(icon, symbolOf(value)));
            }
        }
        return new LcdState(icons, bar, signal, banks, channel, frequency, ctc, line1, cursor1, line2, cursor2);
    }

    private static java.util.Optional<Icon> iconOf(String name) {
        try {
            return java.util.Optional.of(Icon.valueOf(name));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static Indicator symbolOf(String value) {
        return value.isEmpty() ? Indicator.OFF : Indicator.fromSymbol(value.charAt(0));
    }

    /** Text between the brackets, spaces kept: {@code CHN [ 12]} gives {@code " 12"}. */
    private static String bracketed(String line) {
        Matcher m = FIELD.matcher(line);
        return m.matches() ? m.group(2) : "";
    }

    private static String pad(String text) {
        return text.length() >= LcdState.LINE_WIDTH
                ? text
                : text + " ".repeat(LcdState.LINE_WIDTH - text.length());
    }
}
