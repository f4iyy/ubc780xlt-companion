package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Integration: detection, connection, cable removal and reconnection against the simulated scanner. */
class ConnectionFlowIT {

    @Test
    void detectConnectLoseAndRecoverTheLink() throws Exception {
        FakeScanner scanner = new FakeScanner("BC780XLT", "1000000001", "103", 19200);
        FakeLinkFactory factory = new FakeLinkFactory()
                .addPort("COM1", null)
                .addPort("COM5", scanner);
        ConnectionSettings settings = new ConnectionSettings(50, 0);

        List<DetectedScanner> found = new ScannerDetector(factory, settings).scanAll(p -> { });
        assertEquals(1, found.size());
        assertEquals("COM5", found.get(0).port());
        assertEquals(19200, found.get(0).baud());

        try (ConnectionManager manager = new ConnectionManager(factory, settings)) {
            manager.connect("COM5", 0);
            assertEquals(ConnectionState.CONNECTED, manager.state());
            assertEquals("VR1.00", manager.send("VR").text());

            scanner.unplug();
            manager.tick();
            assertEquals(ConnectionState.LOST, manager.state());

            scanner.plugIn();
            manager.tick();
            manager.tick();
            assertEquals(ConnectionState.CONNECTED, manager.state());
            assertEquals(19200, manager.baud());
        }
        assertFalse(scanner.isAutoGenerating("QU"));
    }
}
