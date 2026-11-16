package io.github.f4iyy.ubc780xlt.model;

/** Receiver modulation; {@code AUTO} lets the scanner choose from its band plan. */
public enum Modulation {
    AUTO,
    AM,
    FM,
    NFM,
    WFM;

    /** Parses a name, case-insensitive; {@code null} when unknown. */
    public static Modulation parse(String text) {
        if (text == null) {
            return null;
        }
        for (Modulation m : values()) {
            if (m.name().equalsIgnoreCase(text.strip())) {
                return m;
            }
        }
        return null;
    }
}
