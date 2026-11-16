package io.github.f4iyy.ubc780xlt.protocol.lcd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LcdParserTest {

    /** A real answer captured from a scanner (docs/LCD.log): remote mode, scanning, no signal. */
    private static List<String> idleCapture() throws IOException {
        try (var in = LcdParserTest.class.getResourceAsStream("/lcd/idle.log")) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.US_ASCII).split("\r?\n"));
        }
    }

    @Test
    void anAnswerHas41Lines() throws IOException {
        assertEquals(41, LcdParser.LINE_COUNT);
        assertEquals(41, idleCapture().size());
    }

    @Test
    void parsesTheRealCapture() throws IOException {
        LcdState s = LcdParser.parse(idleCapture());

        assertEquals(Indicator.ON, s.icon(Icon.BANK));
        assertEquals(Indicator.ON, s.icon(Icon.SCAN));
        assertEquals(Indicator.ON, s.icon(Icon.CHAN));
        assertEquals(Indicator.ON, s.icon(Icon.RMT));
        assertEquals(Indicator.ON, s.icon(Icon.DATA));
        assertEquals(Indicator.OFF, s.icon(Icon.P));
        assertEquals(Indicator.OFF, s.icon(Icon.FM));

        assertEquals("-".repeat(29), s.bar());
        assertEquals("------", s.signal());
        assertEquals(0, s.signalBars());
        assertEquals("+--------*", s.banks());
        assertEquals("470", s.channel());
        assertEquals("        ", s.frequency());
        assertEquals("      ", s.ctc());
        assertEquals(" ".repeat(16), s.line1());
        assertEquals("> SCANNING      ", s.line2());
        assertEquals(" ".repeat(16), s.line2Cursor());
    }

    @Test
    void parsesFlashingValuesAndTextFields() {
        List<String> lines = new ArrayList<>(List.of(
                "P *", "FM +", "CTCSS +", "SMT +++---", "BNK ++*+++++--",
                "CHN [ 12]", "FRQ [ 43. 7400]", "CTC [ 123.0]",
                "LINE1 [25cm Amateur   ][             _  ]",
                "LINE2 [Uniden Group A ][                ]"));
        LcdState s = LcdParser.parse(lines);

        assertEquals(Indicator.FLASH, s.icon(Icon.P));
        assertEquals(Indicator.ON, s.icon(Icon.FM));
        assertEquals(3, s.signalBars());
        assertEquals(" 12", s.channel());
        assertEquals(" 43. 7400", s.frequency());
        assertEquals(" 123.0", s.ctc());
        assertEquals("25cm Amateur    ", s.line1());
        assertEquals("             _  ", s.line1Cursor());
        assertEquals("Uniden Group A  ", s.line2());
    }

    @Test
    void toleratesGarbageAndMissingLines() {
        LcdState s = LcdParser.parse(List.of("", "X", "FOO +", "CHN 12", "LINE1 broken", "BNK"));
        assertEquals(Indicator.OFF, s.icon(Icon.SCAN));
        assertEquals("", s.channel());
        assertEquals(" ".repeat(16), s.line1());
        assertEquals("----------", s.banks());
    }

    @Test
    void blankDisplay() {
        LcdState b = LcdState.blank();
        assertEquals(Indicator.OFF, b.icon(Icon.RMT));
        assertEquals(0, b.signalBars());
        assertEquals(29, b.bar().length());
    }

    @Test
    void indicatorBlinking() {
        assertTrue(Indicator.ON.isLit(false));
        assertFalse(Indicator.OFF.isLit(true));
        assertTrue(Indicator.FLASH.isLit(true));
        assertFalse(Indicator.FLASH.isLit(false));
        assertEquals(Indicator.OFF, Indicator.fromSymbol('?'));
    }
}
