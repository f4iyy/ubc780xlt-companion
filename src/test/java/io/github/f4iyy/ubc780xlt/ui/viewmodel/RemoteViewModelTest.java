package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.ScannerKey;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.protocol.lcd.Icon;
import io.github.f4iyy.ubc780xlt.protocol.lcd.Indicator;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoteViewModelTest {

    @TempDir
    Path dir;

    private FakeScanner scanner;
    private ConnectionManager manager;
    private AppConfig config;
    private Messages messages;
    private RemoteViewModel vm;

    @BeforeEach
    void setUp() {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        config = new AppConfig(dir.resolve("config.properties"));
        messages = Messages.of("en");
        vm = new RemoteViewModel(manager, config, messages, Runnable::run, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void startsBlankAndDisconnected() {
        assertFalse(vm.connected().get());
        assertEquals(Indicator.OFF, vm.lcd().get().icon(Icon.RMT));
    }

    @Test
    void doesNotPollWhenDisconnected() {
        vm.pollOnce();
        assertTrue(scanner.received().isEmpty());
    }

    @Test
    void pollingFillsTheDisplayOnceConnected() throws IOException {
        manager.connect("P", 0);
        assertTrue(vm.connected().get());

        vm.pollOnce();

        assertEquals("183", vm.lcd().get().channel());
        assertEquals(Indicator.ON, vm.lcd().get().icon(Icon.RMT));
        assertEquals("> SCANNING      ", vm.lcd().get().line2());
    }

    @Test
    void displayGoesBlankWhenTheLinkIsLost() throws IOException {
        manager.connect("P", 0);
        vm.pollOnce();
        manager.disconnect();
        assertFalse(vm.connected().get());
        assertEquals("", vm.lcd().get().channel());
    }

    @Test
    void aFailedRefreshIsIgnored() throws IOException {
        manager.connect("P", 0);
        scanner.unplug();
        vm.pollOnce(); // IOException: handled, no exception escapes
        assertFalse(manager.state() == io.github.f4iyy.ubc780xlt.protocol.ConnectionState.CONNECTED);
    }

    @Test
    void keysAreSentToTheScanner() throws IOException {
        manager.connect("P", 0);
        vm.pressKey(ScannerKey.MAN, false);
        vm.pressKey(ScannerKey.LOCKOUT, true);
        assertTrue(scanner.received().contains("KEY01"));
        assertTrue(scanner.received().contains("KEY06H"));
        vm.pollOnce();
        assertEquals(Indicator.ON, vm.lcd().get().icon(Icon.HOLD));
    }

    @Test
    void aFailingKeyDoesNotThrow() throws IOException {
        manager.connect("P", 0);
        scanner.unplug();
        vm.pressKey(ScannerKey.SCAN, false);
    }

    @Test
    void tuneValidatesTheTypedFrequency() throws IOException {
        manager.connect("P", 0);

        vm.tune("abc", Step.AUTO, Modulation.AUTO);
        assertEquals(messages.get("tune.invalid"), vm.tuneMessage().get());

        vm.tune("700", Step.AUTO, Modulation.AUTO);
        assertEquals(messages.get("tune.outOfRange", "700"), vm.tuneMessage().get());
        assertFalse(scanner.received().contains("RF07000000"));
    }

    @Test
    void tuneSendsTheSequenceAndReportsSuccess() throws IOException {
        manager.connect("P", 0);
        vm.tune("145,475", Step.K12_5, Modulation.FM);
        assertEquals(messages.get("tune.done", "145.475"), vm.tuneMessage().get());
        assertTrue(scanner.received().contains("RF01454750"));
        vm.pollOnce();
        assertEquals(" 145.4750", vm.lcd().get().frequency());
    }

    @Test
    void tuneReportsFailures() throws IOException {
        manager.connect("P", 0);
        scanner.garbleNext(5);
        vm.tune("145.475", Step.AUTO, Modulation.AUTO);
        assertTrue(vm.tuneMessage().get().startsWith("Tune failed"), vm.tuneMessage().get());
    }

    @Test
    void pollingRunsOnAScheduleAndTheIntervalCanChange() throws Exception {
        manager.connect("P", 0);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            vm.startPolling(scheduler);
            vm.setPollingMs(100);
            assertEquals(100, config.pollingMs());
            long deadline = System.currentTimeMillis() + 3_000;
            while (vm.lcd().get().channel().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals("183", vm.lcd().get().channel());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void settingTheIntervalBeforePollingStartsOnlyStoresIt() {
        vm.setPollingMs(500);
        assertEquals(500, config.pollingMs());
    }
}
