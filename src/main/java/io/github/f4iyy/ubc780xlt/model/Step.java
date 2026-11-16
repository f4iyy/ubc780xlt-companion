package io.github.f4iyy.ubc780xlt.model;

/** Frequency steps; {@code AUTO} lets the scanner choose from its band plan. */
public enum Step {
    AUTO("AUTO"),
    K5("5K"),
    K7_5("7.5K"),
    K10("10K"),
    K12_5("12.5K"),
    K25("25K"),
    K50("50K"),
    K100("100K");

    private final String command;

    Step(String command) {
        this.command = command;
    }

    /** The text used by the scanner ({@code ST} command) and in the XLSX file. */
    public String command() {
        return command;
    }

    @Override
    public String toString() {
        return command;
    }

    /** Parses {@code AUTO}, {@code 12.5K}... case-insensitive; {@code null} when unknown. */
    public static Step parse(String text) {
        if (text == null) {
            return null;
        }
        for (Step s : values()) {
            if (s.command.equalsIgnoreCase(text.strip())) {
                return s;
            }
        }
        return null;
    }
}
