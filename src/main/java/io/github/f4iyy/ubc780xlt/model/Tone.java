package io.github.f4iyy.ubc780xlt.model;

import java.util.Optional;

/**
 * CTCSS/DCS setting of a channel.
 *
 * @param code the scanner's code, 0 (off) to 142 (see {@link ToneTable})
 * @param locked tone lock: the squelch opens only with the right tone ({@code CS###L})
 */
public record Tone(int code, boolean locked) {

    public static final Tone OFF = new Tone(0, false);

    public Tone {
        if (code < 0 || code > ToneTable.MAX_CODE) {
            throw new IllegalArgumentException("Tone code out of range: " + code);
        }
        if (code == 0) {
            locked = false;
        }
    }

    public boolean isOff() {
        return code == 0;
    }

    /** {@code OFF}, {@code 67.0 Hz}, {@code 67.0 Hz L}, {@code DCS 023}... */
    public String label() {
        return ToneTable.label(code) + (locked ? " L" : "");
    }

    /** The 3-digit code of the scanner: {@code 001}, {@code 001L}. */
    public String scannerCode() {
        return String.format("%03d%s", code, locked ? "L" : "");
    }

    @Override
    public String toString() {
        return label();
    }

    public static Optional<Tone> parse(String text) {
        String t = text == null ? "" : text.strip();
        boolean locked = false;
        if (t.endsWith(" L") || t.endsWith(" l")) {
            locked = true;
            t = t.substring(0, t.length() - 2).strip();
        }
        int code = ToneTable.codeOf(t);
        return code < 0 ? Optional.empty() : Optional.of(new Tone(code, locked));
    }
}
