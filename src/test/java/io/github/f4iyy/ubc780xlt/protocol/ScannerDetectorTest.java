package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScannerDetectorTest {

    private static final ConnectionSettings FAST = new ConnectionSettings(50, 0);

    @Test
    void identifyAtTheRightSpeed() {
        ScannerDetector d = new ScannerDetector(FakeLinkFactory.withSampleScanner(), FAST);
        DetectedScanner found = d.identify("SIM1", 9600).orElseThrow();
        assertEquals("SIM1", found.port());
        assertEquals(9600, found.baud());
        assertTrue(found.isSupported());
    }

    @Test
    void identifyFailsAtTheWrongSpeedOrOnAMissingPort() {
        ScannerDetector d = new ScannerDetector(FakeLinkFactory.withSampleScanner(), FAST);
        assertEquals(Optional.empty(), d.identify("SIM1", 2400));
        assertEquals(Optional.empty(), d.identify("NOPE", 9600));
    }

    @Test
    void errAnswerIsRetriedBecauseTheFirstBytesMayBeGarbled() {
        FakeScanner scanner = FakeScanner.bc780xlt();
        scanner.garbleNext(2);
        ScannerDetector d = new ScannerDetector(new FakeLinkFactory().addPort("P", scanner), FAST);

        assertTrue(d.identify("P", 9600).isPresent());
        assertEquals(List.of("SI", "SI", "SI"), scanner.received());
    }

    @Test
    void givesUpAfterThreeErrAnswers() {
        FakeScanner scanner = FakeScanner.bc780xlt();
        scanner.garbleNext(10);
        ScannerDetector d = new ScannerDetector(new FakeLinkFactory().addPort("P", scanner), FAST);

        assertEquals(Optional.empty(), d.identify("P", 9600));
        assertEquals(3, scanner.received().size());
    }

    @Test
    void probeFindsTheSpeedAutomatically() {
        for (int baud : ScannerDetector.BAUDS) {
            FakeLinkFactory f = new FakeLinkFactory()
                    .addPort("P", new FakeScanner("BC780XLT", "1000000001", "103", baud));
            DetectedScanner found = new ScannerDetector(f, FAST).probe("P").orElseThrow();
            assertEquals(baud, found.baud());
        }
    }

    @Test
    void probeGivesUpOnAnEmptyPort() {
        FakeLinkFactory f = new FakeLinkFactory().addPort("P", null);
        assertEquals(Optional.empty(), new ScannerDetector(f, FAST).probe("P"));
    }

    @Test
    void scanAllKeepsOnlySupportedScanners() {
        FakeLinkFactory f = new FakeLinkFactory()
                .addPort("COM1", null)
                .addPort("COM2", FakeScanner.bc780xlt())
                .addPort("COM3", new FakeScanner("BC245XLT", "000000228", "102", 9600));
        List<String> visited = new ArrayList<>();

        List<DetectedScanner> found = new ScannerDetector(f, FAST).scanAll(visited::add);

        assertEquals(List.of("COM1", "COM2", "COM3"), visited);
        assertEquals(1, found.size());
        assertEquals("COM2", found.get(0).port());
    }
}
