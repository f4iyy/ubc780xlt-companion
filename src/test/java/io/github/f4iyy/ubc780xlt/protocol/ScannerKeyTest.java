package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerKeyTest {

    @Test
    void commandsFollowTheUnidenKeyTable() {
        assertEquals("KEY00", ScannerKey.SCAN.command(false));
        assertEquals("KEY01", ScannerKey.MAN.command(false));
        assertEquals("KEY03", ScannerKey.DOT.command(false));
        assertEquals("KEY04", ScannerKey.ENTER.command(false));
        assertEquals("KEY05", ScannerKey.PRI.command(false));
        assertEquals("KEY06", ScannerKey.LOCKOUT.command(false));
        assertEquals("KEY07", ScannerKey.HOLD_UP.command(false));
        assertEquals("KEY08", ScannerKey.LIMIT_DOWN.command(false));
        assertEquals("KEY09", ScannerKey.SEARCH.command(false));
        assertEquals("KEY10", ScannerKey.WEATHER.command(false));
        assertEquals("KEY11", ScannerKey.MENU.command(false));
        assertEquals("KEY12", ScannerKey.SELECT.command(false));
        assertEquals("KEY13", ScannerKey.TRUNK.command(false));
        assertEquals("KEY14", ScannerKey.SELECT_PUSH.command(false));
    }

    @Test
    void longPressAddsH() {
        assertEquals("KEY06H", ScannerKey.LOCKOUT.command(true));
        assertEquals("KEY12H", ScannerKey.SELECT.command(true));
    }

    @Test
    void digitsUseKey02WithTheDigit() {
        for (int d = 0; d <= 9; d++) {
            ScannerKey k = ScannerKey.digit(d);
            assertTrue(k.isDigit());
            assertEquals("KEY02 " + d, k.command(false));
            assertEquals("KEY02 " + d, k.command(true));
        }
        assertFalse(ScannerKey.SCAN.isDigit());
    }

    @Test
    void onlyHoldingEnterGetsNoAnswer() {
        assertFalse(ScannerKey.ENTER.answers(true));
        assertTrue(ScannerKey.ENTER.answers(false));
        assertTrue(ScannerKey.SCAN.answers(true));
    }
}
