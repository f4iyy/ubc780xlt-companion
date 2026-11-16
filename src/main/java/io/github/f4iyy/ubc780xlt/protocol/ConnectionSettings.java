package io.github.f4iyy.ubc780xlt.protocol;

/**
 * Timing of the serial exchange.
 *
 * @param timeoutMs maximum wait for an answer
 * @param interCommandDelayMs minimum pause between two commands (the scanner needs one)
 */
public record ConnectionSettings(int timeoutMs, int interCommandDelayMs) {

    public static ConnectionSettings defaults() {
        return new ConnectionSettings(500, 10);
    }
}
