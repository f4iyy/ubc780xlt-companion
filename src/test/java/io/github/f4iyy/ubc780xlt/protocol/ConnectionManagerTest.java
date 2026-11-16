package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectionManagerTest {

    private static final ConnectionSettings FAST = new ConnectionSettings(50, 0);

    private FakeScanner scanner;
    private ConnectionManager manager;
    private List<ConnectionState> states;

    @BeforeEach
    void setUp() {
        scanner = FakeScanner.bc780xlt();
        FakeLinkFactory factory = new FakeLinkFactory().addPort("P", scanner).addPort("EMPTY", null)
                .addPort("OTHER", new FakeScanner("BC245XLT", "000000228", "102", 9600));
        manager = new ConnectionManager(factory, FAST);
        states = new ArrayList<>();
        manager.addListener(states::add);
    }

    @Test
    void startsDisconnected() {
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
        assertTrue(manager.info().isEmpty());
    }

    @Test
    void connectsWithAutoDetectedSpeed() throws IOException {
        manager.connect("P", 0);

        assertEquals(ConnectionState.CONNECTED, manager.state());
        assertEquals(9600, manager.baud());
        assertEquals("P", manager.port());
        assertEquals("BC780XLT", manager.info().orElseThrow().model());
        assertEquals(List.of(ConnectionState.CONNECTING, ConnectionState.CONNECTED), states);
    }

    @Test
    void connectsWithAFixedSpeed() throws IOException {
        manager.connect("P", 9600);
        assertEquals(ConnectionState.CONNECTED, manager.state());
    }

    @Test
    void fixedWrongSpeedMeansNotFound() {
        assertThrows(ScannerNotFoundException.class, () -> manager.connect("P", 4800));
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void notFoundTellsWhichSpeedsWereTried() {
        ScannerNotFoundException auto = assertThrows(ScannerNotFoundException.class, () -> manager.connect("EMPTY", 0));
        assertEquals("9600, 19200, 4800, 2400", auto.speeds());
        ScannerNotFoundException fixed = assertThrows(ScannerNotFoundException.class, () -> manager.connect("P", 4800));
        assertEquals("4800", fixed.speeds());
        assertTrue(fixed.getMessage().contains("4800 baud"));
    }

    @Test
    void emptyPortMeansNotFound() {
        assertThrows(ScannerNotFoundException.class, () -> manager.connect("EMPTY", 0));
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void theUsModelNameIsAccepted() throws IOException {
        FakeLinkFactory factory = new FakeLinkFactory()
                .addPort("US", new FakeScanner("UBC780XLT", "0000000000", "103", 19200));
        ConnectionManager m = new ConnectionManager(factory, FAST);
        m.connect("US", 0);
        assertEquals(ConnectionState.CONNECTED, m.state());
        assertEquals(19200, m.baud());
    }

    @Test
    void anotherModelIsRefused() {
        assertThrows(UnsupportedScannerException.class, () -> manager.connect("OTHER", 0));
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void switchesAutoGeneratingCommandsOffOnConnectionAndOnDisconnect() throws IOException {
        manager.connect("P", 0);
        assertTrue(scanner.received().containsAll(List.of("IDF", "QUF", "RIF")));

        // The scanner was left in a chatty state by another program: a new session must stop it.
        scanner.received();
        manager.send("QUN");
        assertTrue(scanner.isAutoGenerating("QU"));
        manager.disconnect();
        assertFalse(scanner.isAutoGenerating("QU"));
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void sendRequiresAConnection() {
        assertThrows(IOException.class, () -> manager.send("SI"));
    }

    @Test
    void sendReturnsTheAnswer() throws IOException {
        manager.connect("P", 0);
        assertEquals("VR1.00", manager.send("VR").text());
    }

    @Test
    void heartbeatKeepsTheConnectionWhenAllIsWell() throws IOException {
        manager.connect("P", 0);
        manager.tick();
        assertEquals(ConnectionState.CONNECTED, manager.state());
    }

    @Test
    void tickDoesNothingWhenDisconnected() {
        manager.tick();
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void lostLinkThenReconnection() throws IOException {
        manager.connect("P", 0);

        scanner.unplug();
        manager.tick();
        assertEquals(ConnectionState.LOST, manager.state());

        manager.tick();
        assertEquals(ConnectionState.RECONNECTING, manager.state());
        manager.tick();
        assertEquals(ConnectionState.RECONNECTING, manager.state());

        scanner.plugIn();
        manager.tick();
        assertEquals(ConnectionState.CONNECTED, manager.state());
        assertEquals(
                List.of(ConnectionState.CONNECTING, ConnectionState.CONNECTED, ConnectionState.LOST,
                        ConnectionState.RECONNECTING, ConnectionState.CONNECTED),
                states);
    }

    @Test
    void sendOnABrokenLinkMovesToLost() throws IOException {
        manager.connect("P", 0);
        scanner.unplug();
        assertThrows(IOException.class, () -> manager.send("SI"));
        assertEquals(ConnectionState.LOST, manager.state());
    }

    @Test
    void noReconnectionAfterAnExplicitDisconnect() throws IOException {
        manager.connect("P", 0);
        manager.disconnect();
        manager.tick();
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    @Test
    void reconnectionToAReplacedDeviceIsRefused() throws IOException {
        FakeLinkFactory factory = new FakeLinkFactory().addPort("P", scanner);
        ConnectionManager m = new ConnectionManager(factory, FAST);
        m.connect("P", 0);
        scanner.unplug();
        m.tick();
        // Another device appears on the same port.
        factory.addPort("P", new FakeScanner("BC245XLT", "1", "102", 9600));
        m.tick();
        assertEquals(ConnectionState.RECONNECTING, m.state());
    }

    @Test
    void schedulerDrivesTheKeepAlive() throws Exception {
        manager.connect("P", 0);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            manager.start(scheduler, 10);
            scanner.unplug();
            awaitState(ConnectionState.LOST, ConnectionState.RECONNECTING);
            scanner.plugIn();
            awaitState(ConnectionState.CONNECTED);
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void closeDisconnects() throws IOException {
        manager.connect("P", 0);
        manager.close();
        assertEquals(ConnectionState.DISCONNECTED, manager.state());
    }

    private void awaitState(ConnectionState... accepted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            ConnectionState now = manager.state();
            for (ConnectionState s : accepted) {
                if (s == now) {
                    return;
                }
            }
            Thread.sleep(5);
        }
        throw new AssertionError("State " + List.of(accepted) + " not reached, still " + manager.state());
    }

    @Test
    void readsTheDisplay() throws IOException {
        manager.connect("P", 0);
        var lcd = manager.readLcd();
        assertEquals(io.github.f4iyy.ubc780xlt.protocol.lcd.Indicator.ON,
                lcd.icon(io.github.f4iyy.ubc780xlt.protocol.lcd.Icon.RMT));
        assertEquals("183", lcd.channel());
        assertEquals(3, lcd.signalBars());
    }

    @Test
    void pressesKeys() throws IOException {
        manager.connect("P", 0);
        manager.pressKey(ScannerKey.MAN, false);
        manager.pressKey(ScannerKey.digit(5), false);
        manager.pressKey(ScannerKey.LOCKOUT, true);
        assertTrue(scanner.received().containsAll(List.of("KEY01", "KEY02 5", "KEY06H")));
        assertEquals(1, scanner.mode());
    }

    @Test
    void holdingEnterLeavesRemoteModeWithoutAnswer() throws IOException {
        manager.connect("P", 0);
        manager.pressKey(ScannerKey.ENTER, true);
        assertEquals(ConnectionState.CONNECTED, manager.state(), "silence is expected, not a lost link");
        assertEquals(io.github.f4iyy.ubc780xlt.protocol.lcd.Indicator.OFF,
                manager.readLcd().icon(io.github.f4iyy.ubc780xlt.protocol.lcd.Icon.RMT));
    }

    @Test
    void aSilentScannerDoesNotMeanALostLink() throws IOException {
        manager.connect("P", 0);
        // Asking for 5 lines of a one-line answer: the scanner "stops talking" before the end.
        assertThrows(ScannerTimeoutException.class, () -> manager.sendLines("SI", 5));
        assertEquals(ConnectionState.CONNECTED, manager.state());
    }

    @Test
    void aRefusedKeyIsReported() throws IOException {
        manager.connect("P", 0);
        scanner.garbleNext(1);
        assertThrows(IOException.class, () -> manager.pressKey(ScannerKey.SCAN, false));
    }

    @Test
    void sendLinesRequiresAConnectionAndDetectsABrokenLink() throws IOException {
        assertThrows(IOException.class, () -> manager.sendLines("LCD", 41));
        manager.connect("P", 0);
        scanner.unplug();
        assertThrows(IOException.class, () -> manager.sendLines("LCD", 41));
        assertEquals(ConnectionState.LOST, manager.state());
    }
}
