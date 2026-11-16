package io.github.f4iyy.ubc780xlt.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import io.github.f4iyy.ubc780xlt.transfer.ScannerFormat.ChannelLine;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScannerFormatTest {

    @Test
    void bankLetters() {
        assertEquals("A", ScannerFormat.letter(1));
        assertEquals("J", ScannerFormat.letter(10));
    }

    @Test
    void parsesTheDocumentedChannelLine() {
        ChannelLine l = ScannerFormat.parseChannel("C015 F04060125 TF DN LF AF RF N000").orElseThrow();
        assertEquals(15, l.channel());
        assertEquals(new BigDecimal("406.0125"), l.mhz());
        assertFalse(l.trunk());
        assertTrue(l.delayOn());
        assertFalse(l.lockout());
        assertFalse(l.att());
        assertFalse(l.rec());
        assertEquals(Tone.OFF, l.tone());
    }

    @Test
    void parsesTheShortFormWithoutRecordingAndAToneLock() {
        ChannelLine l = ScannerFormat.parseChannel("C001 F00290050 TN DF LN AN N018L").orElseThrow();
        assertTrue(l.trunk());
        assertTrue(l.lockout());
        assertTrue(l.att());
        assertFalse(l.rec());
        assertEquals(new Tone(18, true), l.tone());
        assertEquals(new BigDecimal("29.0050"), l.mhz());
    }

    @Test
    void rejectsOtherChannelAnswers() {
        assertEquals(Optional.empty(), ScannerFormat.parseChannel("NG"));
        assertEquals(Optional.empty(), ScannerFormat.parseChannel("C001 F123 TF DF LF AF N000"));
        assertEquals(Optional.empty(), ScannerFormat.parseChannel("C001 F00290050 TN DF LN AN N200"));
    }

    @Test
    void parsesTags() {
        assertEquals("RELAIS", ScannerFormat.parseTag("TA C 001 RELAIS          ", "TA C 001 ").orElseThrow());
        assertEquals("", ScannerFormat.parseTag("TA C 001 " + " ".repeat(16), "TA C 001 ").orElseThrow());
        assertEquals("", ScannerFormat.parseTag("TA C 001", "TA C 001 ").orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parseTag("ERR", "TA C 001 "));
        assertEquals(Optional.empty(), ScannerFormat.parseTag("TA C 002 X", "TA C 001 "));
    }

    @Test
    void parsesSearchLimits() {
        assertEquals(new BigDecimal("150.8150"), ScannerFormat.parseLimit("LL01508150 A").orElseThrow());
        assertEquals(new BigDecimal("150.9650"), ScannerFormat.parseLimit("LU01509650").orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parseLimit("NG"));
    }

    @Test
    void parsesOnOffAnswers() {
        assertEquals(Optional.of(true), ScannerFormat.parseOnOff("BAN C 001", "BA"));
        assertEquals(Optional.of(false), ScannerFormat.parseOnOff("BAF C 001", "BA"));
        assertEquals(Optional.empty(), ScannerFormat.parseOnOff("ERR", "BA"));
    }

    @Test
    void parsesDelayModulationAndStep() {
        assertEquals(Delay.NONE, ScannerFormat.parseDelay("DLF").orElseThrow());
        assertEquals(Delay.S2, ScannerFormat.parseDelay("DL +2").orElseThrow());
        assertEquals(Delay.M10, ScannerFormat.parseDelay("DLN -10").orElseThrow());
        assertEquals(Delay.INFINITE, ScannerFormat.parseDelay("DL +-").orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parseDelay("NG"));
        assertEquals(Optional.empty(), ScannerFormat.parseDelay("DL +3"));

        assertEquals(Modulation.WFM, ScannerFormat.parseModulation("RM WFM").orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parseModulation("RM ---"));
        assertEquals(Optional.empty(), ScannerFormat.parseModulation("OK"));

        assertEquals(Step.K12_5, ScannerFormat.parseStep("ST 12.5K").orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parseStep("ST 13K"));
        assertEquals(Optional.empty(), ScannerFormat.parseStep("OK"));
    }

    @Test
    void priorityIsAChannelNumberOverAll500Channels() {
        assertEquals(1, ScannerFormat.parsePriority("PC A001", 1).orElseThrow());
        assertEquals(51, ScannerFormat.parsePriority("PC B051", 2).orElseThrow(), "seen on a real scanner");
        assertEquals(150, ScannerFormat.parsePriority("PC C150", 3).orElseThrow());
        assertEquals(14, ScannerFormat.parsePriority("PCA014", 1).orElseThrow());
        assertEquals(Optional.empty(), ScannerFormat.parsePriority("PC A000", 1));
        assertEquals(Optional.empty(), ScannerFormat.parsePriority("PC B014", 2), "not a channel of bank B");
        assertEquals(Optional.empty(), ScannerFormat.parsePriority("PC B051", 1), "wrong bank");
        assertEquals(Optional.empty(), ScannerFormat.parsePriority("PC A999", 1));
        assertEquals(Optional.empty(), ScannerFormat.parsePriority("NG", 1));
    }
}
