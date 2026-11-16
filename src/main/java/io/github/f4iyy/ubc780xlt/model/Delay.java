package io.github.f4iyy.ubc780xlt.model;

/** Delay after a transmission before scanning resumes ({@code DL} command): +n seconds, or -n for a fixed pause. */
public enum Delay {
    NONE("NONE", null),
    S1("+1", "+1"),
    S2("+2", "+2"),
    S4("+4", "+4"),
    INFINITE("+-", "+-"),
    M2("-2", "-2"),
    M5("-5", "-5"),
    M10("-10", "-10");

    private final String label;
    private final String command;

    Delay(String label, String command) {
        this.label = label;
        this.command = command;
    }

    /** Text shown in tables and stored in the XLSX file. */
    public String label() {
        return label;
    }

    /** Argument of {@code DLN}; {@code null} for {@link #NONE} (which is {@code DLF}). */
    public String command() {
        return command;
    }

    @Override
    public String toString() {
        return label;
    }

    /** Parses a label ({@code NONE}, {@code +2}, {@code -10}...); {@code null} when unknown. */
    public static Delay parse(String text) {
        if (text == null) {
            return null;
        }
        for (Delay d : values()) {
            if (d.label.equalsIgnoreCase(text.strip())) {
                return d;
            }
        }
        return null;
    }
}
