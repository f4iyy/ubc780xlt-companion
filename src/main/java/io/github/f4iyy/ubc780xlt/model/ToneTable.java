package io.github.f4iyy.ubc780xlt.model;

import java.util.List;
import java.util.stream.IntStream;

/** The scanner's CTCSS/DCS code table: 001-038 are CTCSS tones, 039-142 are DCS codes. */
public final class ToneTable {

    static final String[] CTCSS = {
        "67.0", "71.9", "74.4", "77.0", "79.7", "82.5", "85.4", "88.5", "91.5", "94.8", "97.4", "100.0", "103.5",
        "107.2", "110.9", "114.8", "118.8", "123.0", "127.3", "131.8", "136.5", "141.3", "146.2", "151.4", "156.7",
        "162.2", "167.9", "173.8", "179.9", "186.2", "192.8", "203.5", "210.7", "218.1", "225.7", "233.6", "241.8",
        "250.3"
    };

    static final int[] DCS = {
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74, 114, 115, 116, 122, 125, 131, 132, 134, 143,
        145, 152, 155, 156, 162, 165, 172, 174, 205, 212, 223, 225, 226, 243, 244, 245, 246, 251, 252, 255, 261, 263,
        265, 266, 271, 274, 306, 311, 315, 325, 331, 332, 343, 346, 351, 356, 364, 365, 371, 411, 412, 413, 423, 431,
        432, 445, 446, 452, 454, 455, 462, 464, 465, 466, 503, 506, 516, 523, 526, 532, 546, 565, 606, 612, 624, 627,
        631, 632, 654, 662, 664, 703, 712, 723, 731, 732, 734, 743, 754
    };

    public static final int MAX_CODE = CTCSS.length + DCS.length;

    private ToneTable() {
    }

    /** Label of a code: {@code OFF}, {@code 67.0 Hz} or {@code DCS 023}. */
    public static String label(int code) {
        if (code == 0) {
            return "OFF";
        }
        if (code >= 1 && code <= CTCSS.length) {
            return CTCSS[code - 1] + " Hz";
        }
        if (code > CTCSS.length && code <= MAX_CODE) {
            return String.format("DCS %03d", DCS[code - CTCSS.length - 1]);
        }
        throw new IllegalArgumentException("Tone code out of range: " + code);
    }

    /** Every code from 0 to {@link #MAX_CODE}. */
    public static List<Integer> codes() {
        return IntStream.rangeClosed(0, MAX_CODE).boxed().toList();
    }

    /** Code for a label as produced by {@link #label}; {@code -1} when unknown. */
    public static int codeOf(String label) {
        String t = label == null ? "" : label.strip();
        for (int code = 0; code <= MAX_CODE; code++) {
            if (label(code).equalsIgnoreCase(t)) {
                return code;
            }
        }
        return -1;
    }
}
