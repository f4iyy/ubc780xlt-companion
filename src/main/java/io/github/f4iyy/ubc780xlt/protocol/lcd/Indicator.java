package io.github.f4iyy.ubc780xlt.protocol.lcd;

/** State of one LCD icon: {@code +} on, {@code -} off, {@code *} flashing. */
public enum Indicator {
    OFF,
    ON,
    FLASH;

    public static Indicator fromSymbol(char symbol) {
        return switch (symbol) {
            case '+' -> ON;
            case '*' -> FLASH;
            default -> OFF;
        };
    }

    /** Whether the icon should be drawn lit at this blink phase. */
    public boolean isLit(boolean blinkPhase) {
        return this == ON || (this == FLASH && blinkPhase);
    }
}
