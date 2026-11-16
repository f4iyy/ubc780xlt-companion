package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FrequencyRangesTest {

    private static BigDecimal mhz(String s) {
        return new BigDecimal(s);
    }

    @Test
    void bandEdgesAreInside() {
        for (String f : new String[] {"25", "512", "806", "823.9875", "849.0125", "868.9875", "894.0125", "956",
                "1240", "1300", "145.475", "430.35"}) {
            assertTrue(FrequencyRanges.isValid(mhz(f)), f);
        }
    }

    @Test
    void outsideTheBandsIsInvalid() {
        for (String f : new String[] {"24.9999", "512.0001", "700", "824", "849", "869", "893.9", "956.5", "1300.1"}) {
            assertFalse(FrequencyRanges.isValid(mhz(f)), f);
        }
    }

    @Test
    void scannerFormatIsEightDigitsInHundredsOfHertz() {
        assertEquals("01454750", FrequencyRanges.toScannerFormat(mhz("145.475")));
        assertEquals("04303500", FrequencyRanges.toScannerFormat(mhz("430.35")));
        assertEquals("00250000", FrequencyRanges.toScannerFormat(mhz("25")));
        assertEquals("13000000", FrequencyRanges.toScannerFormat(mhz("1300")));
        assertEquals("04060125", FrequencyRanges.toScannerFormat(mhz("406.0125")));
    }

    @Test
    void parsesTypedFrequencies() {
        assertEquals(Optional.of(mhz("145.475")), FrequencyRanges.parseMhz("145.475"));
        assertEquals(Optional.of(mhz("145.475")), FrequencyRanges.parseMhz(" 145,475 "));
        assertEquals(Optional.empty(), FrequencyRanges.parseMhz(""));
        assertEquals(Optional.empty(), FrequencyRanges.parseMhz(null));
        assertEquals(Optional.empty(), FrequencyRanges.parseMhz("abc"));
        assertEquals(Optional.empty(), FrequencyRanges.parseMhz("-5"));
        assertEquals(Optional.empty(), FrequencyRanges.parseMhz("0"));
    }
}
