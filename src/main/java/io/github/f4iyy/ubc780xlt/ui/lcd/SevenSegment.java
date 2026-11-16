package io.github.f4iyy.ubc780xlt.ui.lcd;

/**
 * Seven-segment encoding of the characters the scanner shows in its numeric fields.
 * Segments: a top, b upper right, c lower right, d bottom, e lower left, f upper left, g middle.
 */
public final class SevenSegment {

    public static final int A = 1;
    public static final int B = 1 << 1;
    public static final int C = 1 << 2;
    public static final int D = 1 << 3;
    public static final int E = 1 << 4;
    public static final int F = 1 << 5;
    public static final int G = 1 << 6;

    private SevenSegment() {
    }

    /** Lit segments for {@code c} (a bit mask of {@link #A}..{@link #G}); 0 for a blank or unknown character. */
    public static int segmentsFor(char c) {
        return switch (Character.toLowerCase(c)) {
            case '0' -> A | B | C | D | E | F;
            case '1' -> B | C;
            case '2' -> A | B | D | E | G;
            case '3' -> A | B | C | D | G;
            case '4' -> B | C | F | G;
            case '5' -> A | C | D | F | G;
            case '6' -> A | C | D | E | F | G;
            case '7' -> A | B | C;
            case '8' -> A | B | C | D | E | F | G;
            case '9' -> A | B | C | D | F | G;
            case '-' -> G;
            case '_' -> D;
            case 'a' -> A | B | C | E | F | G;
            case 'b' -> C | D | E | F | G;
            case 'c' -> A | D | E | F;
            case 'd' -> B | C | D | E | G;
            case 'e' -> A | D | E | F | G;
            case 'f' -> A | E | F | G;
            case 'h' -> B | C | E | F | G;
            case 'l' -> D | E | F;
            case 'n' -> C | E | G;
            case 'o' -> C | D | E | G;
            case 'p' -> A | B | E | F | G;
            case 'r' -> E | G;
            case 't' -> D | E | F | G;
            case 'u' -> C | D | E;
            default -> 0;
        };
    }

    /** Number of lit segments, handy for tests and sanity checks. */
    public static int count(int mask) {
        return Integer.bitCount(mask);
    }
}
