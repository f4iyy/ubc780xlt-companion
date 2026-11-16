package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.io.IOException;
import java.io.InterruptedIOException;
import org.junit.jupiter.api.Test;

class ScannerConnectionTest {

    private static ScannerConnection open(FakeLinkFactory factory, String port, int baud, ConnectionSettings s)
            throws IOException {
        return new ScannerConnection(factory.open(port, baud), s);
    }

    @Test
    void sendsACommandAndParsesTheAnswer() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 9600, new ConnectionSettings(100, 0))) {
            assertTrue(c.isOpen());
            Response r = c.send("SI");
            assertEquals("SI BC780XLT,1000000001,103", r.text());
            assertEquals(Response.Kind.ERR, c.send("XYZ").kind());
        }
    }

    @Test
    void silenceBecomesATimeout() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 2400, new ConnectionSettings(100, 0))) {
            ScannerTimeoutException e = assertThrows(ScannerTimeoutException.class, () -> c.send("SI"));
            assertTrue(e.getMessage().contains("SI"));
        }
    }

    @Test
    void waitsBetweenCommands() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 9600, new ConnectionSettings(100, 40))) {
            c.send("SI");
            long start = System.nanoTime();
            c.send("SI");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs >= 30, "second command came after only " + elapsedMs + " ms");
        }
    }

    @Test
    void interruptedWhileWaitingStopsTheCommand() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 9600, new ConnectionSettings(100, 5_000))) {
            c.send("SI");
            Thread.currentThread().interrupt();
            assertThrows(InterruptedIOException.class, () -> c.send("SI"));
            assertTrue(Thread.interrupted(), "interrupt flag must be restored");
        }
    }

    @Test
    void closeClosesTheLink() throws IOException {
        FakeScanner scanner = FakeScanner.bc780xlt();
        ScannerConnection c = open(new FakeLinkFactory().addPort("P", scanner), "P", 9600,
                ConnectionSettings.defaults());
        c.close();
        assertFalse(c.isOpen());
    }

    @Test
    void readsAMultiLineAnswer() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 9600, new ConnectionSettings(100, 0))) {
            var lines = c.sendLines("LCD", 41);
            assertEquals(41, lines.size());
            assertTrue(lines.get(0).startsWith("P "));
            assertTrue(lines.get(40).startsWith("LINE2 ["));
        }
    }

    @Test
    void aMultiLineAnswerThatStopsEarlyIsATimeout() throws IOException {
        FakeLinkFactory factory = FakeLinkFactory.withSampleScanner();
        try (ScannerConnection c = open(factory, "SIM1", 9600, new ConnectionSettings(100, 0))) {
            assertThrows(ScannerTimeoutException.class, () -> c.sendLines("SI", 5));
            // The connection is still usable afterwards.
            assertEquals(1, c.sendLines("SI", 1).size());
        }
    }
}
