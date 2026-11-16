package io.github.f4iyy.ubc780xlt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelBasicsTest {

    // ---- tones ----

    @Test
    void toneTableHas38CtcssAnd104DcsCodes() {
        assertEquals(38, ToneTable.CTCSS.length);
        assertEquals(104, ToneTable.DCS.length);
        assertEquals(142, ToneTable.MAX_CODE);
        assertEquals(143, ToneTable.codes().size());
    }

    @Test
    void toneLabelsMatchTheUnidenTable() {
        assertEquals("OFF", ToneTable.label(0));
        assertEquals("67.0 Hz", ToneTable.label(1));
        assertEquals("123.0 Hz", ToneTable.label(18));
        assertEquals("250.3 Hz", ToneTable.label(38));
        assertEquals("DCS 023", ToneTable.label(39));
        assertEquals("DCS 754", ToneTable.label(142));
        assertThrows(IllegalArgumentException.class, () -> ToneTable.label(143));
        assertThrows(IllegalArgumentException.class, () -> ToneTable.label(-1));
    }

    @Test
    void everyCodeRoundTripsThroughItsLabel() {
        for (int code : ToneTable.codes()) {
            assertEquals(code, ToneTable.codeOf(ToneTable.label(code)));
        }
        assertEquals(-1, ToneTable.codeOf("99 Hz"));
        assertEquals(-1, ToneTable.codeOf(null));
    }

    @Test
    void toneParsingAndLock() {
        assertEquals(new Tone(1, true), Tone.parse("67.0 Hz L").orElseThrow());
        assertEquals("67.0 Hz L", new Tone(1, true).label());
        assertEquals("001L", new Tone(1, true).scannerCode());
        assertEquals("039", new Tone(39, false).scannerCode());
        assertEquals(Tone.OFF, Tone.parse("off").orElseThrow());
        assertEquals(Optional.empty(), Tone.parse("nonsense"));
        assertEquals(Optional.empty(), Tone.parse(null));
        assertTrue(Tone.OFF.isOff());
        assertFalse(new Tone(0, true).locked(), "OFF cannot be locked");
        assertEquals("DCS 023", new Tone(39, false).toString());
        assertThrows(IllegalArgumentException.class, () -> new Tone(143, false));
    }

    // ---- enums ----

    @Test
    void enumsParseTheirLabels() {
        assertEquals(Modulation.NFM, Modulation.parse(" nfm "));
        assertNull(Modulation.parse("xx"));
        assertNull(Modulation.parse(null));
        assertEquals(Step.K12_5, Step.parse("12.5k"));
        assertNull(Step.parse("13K"));
        assertNull(Step.parse(null));
        assertEquals("7.5K", Step.K7_5.toString());
        assertEquals(Delay.M10, Delay.parse("-10"));
        assertEquals(Delay.INFINITE, Delay.parse("+-"));
        assertNull(Delay.parse("+3"));
        assertNull(Delay.parse(null));
        assertEquals("+2", Delay.S2.command());
        assertNull(Delay.NONE.command());
        assertEquals("NONE", Delay.NONE.toString());
    }

    // ---- tags ----

    @Test
    void tagsAreAtMost16PrintableAsciiCharacters() {
        assertTrue(Tags.isValid(""));
        assertTrue(Tags.isValid("0123456789ABCDEF"));
        assertFalse(Tags.isValid("0123456789ABCDEFG"));
        assertFalse(Tags.isValid("café"));
        assertFalse(Tags.isValid(null));
    }

    @Test
    void cleanFixesTags() {
        assertEquals("", Tags.clean(null));
        assertEquals("A B", Tags.clean("  A    B  "));
        assertEquals("CAF?", Tags.clean("CAFÉ"));
        assertEquals("0123456789ABCDEF", Tags.clean("0123456789ABCDEFGHIJ"));
        assertEquals("ABCDEFGHIJKLMNO", Tags.clean("ABCDEFGHIJKLMNO PQRS"));
    }

    // ---- channel data ----

    @Test
    void emptyChannelIsLockedOutWithNoFrequency() {
        ChannelData e = ChannelData.empty();
        assertTrue(e.isEmpty());
        assertTrue(e.lockout());
        assertEquals(Tone.OFF, e.tone());
        assertEquals(Delay.NONE, e.delay());
        assertEquals(Modulation.AUTO, e.modulation());
        assertEquals(Step.AUTO, e.step());
        assertEquals("0.0000", e.frequency().toPlainString());
    }

    @Test
    void frequencyIsKeptWithFourDecimals() {
        ChannelData d = ChannelData.empty().withFrequency(new BigDecimal("145.475"));
        assertEquals("145.4750", d.frequency().toPlainString());
        assertFalse(d.isEmpty());
        assertEquals(d, ChannelData.empty().withFrequency(new BigDecimal("145.4750")));
    }

    @Test
    void channelRoundTripsThroughTheClipboardFormat() {
        ChannelData d = new ChannelData(new BigDecimal("430.35"), new Tone(18, true), "RELAIS", true, false, true,
                false, true, true, Delay.S2, Modulation.FM, Step.K12_5);
        assertEquals(d, ChannelData.fromTsv(d.toTsv()));
    }

    @Test
    void clipboardLinesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> ChannelData.fromTsv("not\ta\tchannel"));
        String good = ChannelData.empty().withFrequency(new BigDecimal("145.5")).toTsv();
        assertThrows(IllegalArgumentException.class, () -> ChannelData.fromTsv(good.replace("145.5000", "abc")));
        assertThrows(IllegalArgumentException.class, () -> ChannelData.fromTsv(good.replace("OFF", "XX")));
        assertThrows(IllegalArgumentException.class, () -> ChannelData.fromTsv(good.replace("NONE", "?")));
        assertThrows(IllegalArgumentException.class, () -> ChannelData.fromTsv(good.replace("\tAUTO\t", "\tZZZ\t")));
    }

    @Test
    void searchAndScanBankDefaults() {
        SearchBankData s = SearchBankData.empty();
        assertTrue(s.isEmpty());
        assertEquals("0.0000", s.lower().toPlainString());
        assertEquals(Modulation.AUTO, s.modulation());
        assertEquals("", new ScanBankData(null).tag());
    }
}
