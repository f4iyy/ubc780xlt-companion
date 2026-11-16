package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScannerInfoTest {

    @Test
    void parsesTheDocumentedAnswer() {
        ScannerInfo info = ScannerInfo.parse("SI BC780XLT,1000000001,103").orElseThrow();
        assertEquals("BC780XLT", info.model());
        assertEquals("1000000001", info.serial());
        assertEquals("103", info.commandVersion());
        assertTrue(info.isSupported());
    }

    @Test
    void theUsModelAnswersUbc780xlt() {
        // Seen on a real scanner: "SI UBC780XLT,0000000000,103"
        ScannerInfo info = ScannerInfo.parse("SI UBC780XLT,0000000000,103").orElseThrow();
        assertEquals("UBC780XLT", info.model());
        assertTrue(info.isSupported());
    }

    @Test
    void lookalikeModelsAreNotSupported() {
        assertFalse(ScannerInfo.parse("SI BC780XLT2,1,2").orElseThrow().isSupported());
        assertFalse(ScannerInfo.parse("SI XBC780XLT,1,2").orElseThrow().isSupported());
    }

    @Test
    void otherModelsParseButAreNotSupported() {
        ScannerInfo info = ScannerInfo.parse("SI BC245XLT,000000228,102").orElseThrow();
        assertEquals("BC245XLT", info.model());
        assertFalse(info.isSupported());
    }

    @Test
    void rejectsMalformedLines() {
        assertEquals(Optional.empty(), ScannerInfo.parse(null));
        assertEquals(Optional.empty(), ScannerInfo.parse("OK"));
        assertEquals(Optional.empty(), ScannerInfo.parse("SI BC780XLT"));
        assertEquals(Optional.empty(), ScannerInfo.parse("SI BC780XLT,1,2,3"));
        assertEquals(Optional.empty(), ScannerInfo.parse("SI ,1,2"));
        assertEquals(Optional.empty(), ScannerInfo.parse("XX BC780XLT,1,2"));
    }
}
