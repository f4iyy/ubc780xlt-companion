package io.github.f4iyy.ubc780xlt.protocol.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.SerialLink;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeScannerTest {

    @Test
    void answersSiLikeTheRealScanner() {
        assertEquals("SI BC780XLT,1000000001,103", FakeScanner.bc780xlt().handle("SI"));
    }

    @Test
    void answersVrAndRejectsUnknownCommands() {
        FakeScanner s = FakeScanner.bc780xlt();
        assertEquals("VR1.00", s.handle("VR"));
        assertEquals("ERR", s.handle("XYZ"));
    }

    @Test
    void autoGeneratingCommandsCanBeSwitchedOnAndOff() {
        FakeScanner s = FakeScanner.bc780xlt();
        for (String cmd : List.of("ID", "QU", "RI")) {
            assertEquals(cmd + "F", s.handle(cmd));
            assertEquals("OK", s.handle(cmd + "N"));
            assertTrue(s.isAutoGenerating(cmd));
            assertEquals(cmd + "N", s.handle(cmd));
            assertEquals("OK", s.handle(cmd + "F"));
            assertFalse(s.isAutoGenerating(cmd));
        }
    }

    @Test
    void recordsWhatItReceived() {
        FakeScanner s = FakeScanner.bc780xlt();
        s.handle("SI");
        s.handle("VR");
        assertEquals(List.of("SI", "VR"), s.received());
    }

    @Test
    void unplugAndPlugIn() {
        FakeScanner s = FakeScanner.bc780xlt();
        assertFalse(s.isUnplugged());
        s.unplug();
        assertTrue(s.isUnplugged());
        s.plugIn();
        assertFalse(s.isUnplugged());
        assertEquals(9600, s.baud());
    }

    @Test
    void linkAnswersOnlyAtTheScannerSpeed() throws IOException {
        FakeLinkFactory factory = new FakeLinkFactory().addPort("P", FakeScanner.bc780xlt());
        try (SerialLink right = factory.open("P", 9600); SerialLink wrong = factory.open("P", 4800)) {
            right.write("SI");
            wrong.write("SI");
            assertEquals("SI BC780XLT,1000000001,103", right.readLine(10));
            assertNull(wrong.readLine(10));
        }
    }

    @Test
    void emptyPortNeverAnswers() throws IOException {
        FakeLinkFactory factory = new FakeLinkFactory().addPort("EMPTY", null);
        try (SerialLink link = factory.open("EMPTY", 9600)) {
            link.write("SI");
            assertNull(link.readLine(10));
        }
    }

    @Test
    void discardInputDropsPendingAnswers() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (SerialLink link = factory.open("SIM1", 9600)) {
            link.write("SI");
            link.discardInput();
            assertNull(link.readLine(10));
        }
    }

    @Test
    void factoryListsPortsAndRefusesUnknownOnes() {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner().addPort("A", null);
        assertEquals(List.of("A", "SIM1"), factory.listPorts());
        assertThrows(IOException.class, () -> factory.open("NOPE", 9600));
    }

    @Test
    void unpluggedScannerBreaksOpenLinksAndRefusesNewOnes() throws IOException {
        FakeScanner scanner = FakeScanner.bc780xlt();
        FakeLinkFactory factory = new FakeLinkFactory().addPort("P", scanner);
        SerialLink link = factory.open("P", 9600);
        assertTrue(link.isOpen());

        scanner.unplug();
        assertThrows(IOException.class, () -> link.write("SI"));
        assertThrows(IOException.class, () -> link.readLine(10));
        assertThrows(IOException.class, link::discardInput);
        assertThrows(IOException.class, () -> factory.open("P", 9600));
    }

    @Test
    void closedLinkRefusesUse() throws IOException {
        SerialLink link = FakeLinkFactory.withSampleScanner().open("SIM1", 9600);
        link.close();
        assertFalse(link.isOpen());
        assertThrows(IOException.class, () -> link.write("SI"));
    }
}
