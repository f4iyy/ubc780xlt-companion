package io.github.f4iyy.ubc780xlt.protocol;

/** Front-panel keys that can be pressed through the {@code KEY} command. */
public enum ScannerKey {
    SCAN(0),
    MAN(1),
    DOT(3),
    ENTER(4),
    PRI(5),
    LOCKOUT(6),
    HOLD_UP(7),
    LIMIT_DOWN(8),
    SEARCH(9),
    WEATHER(10),
    MENU(11),
    SELECT(12),
    TRUNK(13),
    SELECT_PUSH(14),
    DIGIT_0(2, 0),
    DIGIT_1(2, 1),
    DIGIT_2(2, 2),
    DIGIT_3(2, 3),
    DIGIT_4(2, 4),
    DIGIT_5(2, 5),
    DIGIT_6(2, 6),
    DIGIT_7(2, 7),
    DIGIT_8(2, 8),
    DIGIT_9(2, 9);

    private final int code;
    private final int digit;

    ScannerKey(int code) {
        this(code, -1);
    }

    ScannerKey(int code, int digit) {
        this.code = code;
        this.digit = digit;
    }

    public boolean isDigit() {
        return digit >= 0;
    }

    /**
     * The command that does what pressing this key does.
     *
     * @param hold long press ({@code KEYnnH}); digits have no long press, so they ignore it
     */
    public String command(boolean hold) {
        if (isDigit()) {
            return String.format("KEY%02d %d", code, digit);
        }
        return String.format("KEY%02d%s", code, hold ? "H" : "");
    }

    /**
     * Whether the scanner is expected to answer. Holding E/REM leaves remote mode, and the scanner
     * then stays silent (Uniden documentation).
     */
    public boolean answers(boolean hold) {
        return !(this == ENTER && hold);
    }

    public static ScannerKey digit(int value) {
        return valueOf("DIGIT_" + value);
    }
}
