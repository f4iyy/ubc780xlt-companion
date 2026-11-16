package io.github.f4iyy.ubc780xlt.protocol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Frequency bands the BC780XLT can receive, and the scanner's frequency format. */
public final class FrequencyRanges {

    /** Band limits in MHz. */
    public record Band(BigDecimal low, BigDecimal high) {
        boolean contains(BigDecimal mhz) {
            return mhz.compareTo(low) >= 0 && mhz.compareTo(high) <= 0;
        }
    }

    public static final List<Band> BANDS = List.of(
            band("25", "512"),
            band("806", "823.9875"),
            band("849.0125", "868.9875"),
            band("894.0125", "956"),
            band("1240", "1300"));

    private FrequencyRanges() {
    }

    private static Band band(String low, String high) {
        return new Band(new BigDecimal(low), new BigDecimal(high));
    }

    /** Parses a frequency typed in MHz ({@code 145.475} or {@code 145,475}); empty if not a number. */
    public static Optional<BigDecimal> parseMhz(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal v = new BigDecimal(text.strip().replace(',', '.'));
            return v.signum() > 0 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isValid(BigDecimal mhz) {
        return BANDS.stream().anyMatch(b -> b.contains(mhz));
    }

    /**
     * The 8-digit frequency of the {@code RF} command: from the 1 GHz digit to the 100 Hz digit,
     * so 145.475 MHz is {@code 01454750}.
     */
    public static String toScannerFormat(BigDecimal mhz) {
        long hundredsOfHz = mhz.movePointRight(4).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return String.format("%08d", hundredsOfHz);
    }
}
