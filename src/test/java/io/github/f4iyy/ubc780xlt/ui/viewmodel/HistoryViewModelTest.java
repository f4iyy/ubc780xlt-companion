package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.history.HistoryLog;
import io.github.f4iyy.ubc780xlt.history.Reception;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryViewModelTest {

    @TempDir
    Path dir;

    private FakeScanner scanner;
    private ConnectionManager manager;
    private AppConfig config;
    private Messages messages;
    private HistoryViewModel vm;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        config = new AppConfig(dir.resolve("config.properties"));
        messages = Messages.of("en");
        vm = new HistoryViewModel(manager, config, new HistoryLog(dir.resolve("logs"), ZoneOffset.UTC), messages,
                Runnable::run, Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC));
        manager.connect("P", 0);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void aReceptionIsShownLiveThenCompletedAndLogged() throws IOException {
        scanner.setSquelchOpen(true);
        scanner.setSignal(130, 4303500);
        vm.pollOnce();

        assertEquals(1, vm.receptions().size());
        assertTrue(vm.receptions().get(0).inProgress());
        assertEquals("430.3500", vm.receptions().get(0).frequency());

        scanner.setSquelchOpen(false);
        vm.pollOnce();

        assertEquals(1, vm.receptions().size(), "the live row is replaced, not duplicated");
        Reception r = vm.receptions().get(0);
        assertFalse(r.inProgress());
        assertEquals(130, r.maxSignal());
        Path file = dir.resolve("logs/history-2026-11-06.csv");
        assertTrue(Files.exists(file));
        assertEquals(2, Files.readAllLines(file).size());
    }

    @Test
    void newestReceptionIsFirst() {
        for (int i = 0; i < 2; i++) {
            scanner.setSquelchOpen(true);
            scanner.setSignal(100 + i, 4303500L + i);
            vm.pollOnce();
            scanner.setSquelchOpen(false);
            vm.pollOnce();
        }
        assertEquals(2, vm.receptions().size());
        assertEquals("430.3501", vm.receptions().get(0).frequency());
    }

    @Test
    void nothingIsRecordedWhenRecordingIsOffAndTheChoiceIsRemembered() {
        vm.recording().set(false);
        assertFalse(AppConfig.load(dir.resolve("config.properties")).history());

        scanner.setSquelchOpen(true);
        vm.pollOnce();
        assertTrue(vm.receptions().isEmpty());

        vm.recording().set(true);
        assertTrue(AppConfig.load(dir.resolve("config.properties")).history());
        vm.pollOnce();
        assertEquals(1, vm.receptions().size());
    }

    @Test
    void theSavedChoiceIsUsedAtStart() {
        config.setHistory(false);
        HistoryViewModel off = new HistoryViewModel(manager, config, new HistoryLog(dir, ZoneOffset.UTC), messages,
                Runnable::run, Clock.systemUTC());
        assertFalse(off.recording().get());
    }

    @Test
    void pausingEndsTheReceptionAndSendsNothing() {
        scanner.setSquelchOpen(true);
        vm.pollOnce();
        assertTrue(vm.receptions().get(0).inProgress());

        vm.setPaused(true);
        int before = scanner.received().size();
        vm.pollOnce();

        assertEquals(before, scanner.received().size());
        assertFalse(vm.receptions().get(0).inProgress(), "ended by the pause");
        vm.setPaused(false);
    }

    @Test
    void aLostLinkEndsTheReception() {
        scanner.setSquelchOpen(true);
        vm.pollOnce();
        scanner.unplug();
        vm.pollOnce();
        assertFalse(vm.receptions().get(0).inProgress());

        manager.disconnect();
        vm.pollOnce(); // not connected: nothing to do
    }

    @Test
    void clearKeepsTheReceptionInProgress() {
        scanner.setSquelchOpen(true);
        vm.pollOnce();
        scanner.setSquelchOpen(false);
        vm.pollOnce();
        scanner.setSquelchOpen(true);
        vm.pollOnce();
        assertEquals(2, vm.receptions().size());

        vm.clear();
        assertEquals(1, vm.receptions().size());
        assertTrue(vm.receptions().get(0).inProgress());
    }

    @Test
    void aHistoryFileThatCannotBeWrittenIsReported() throws IOException {
        Path blocker = Files.createFile(dir.resolve("blocker"));
        HistoryViewModel v = new HistoryViewModel(manager, config, new HistoryLog(blocker.resolve("x"),
                ZoneOffset.UTC), messages, Runnable::run, Clock.systemUTC());
        scanner.setSquelchOpen(true);
        v.pollOnce();
        scanner.setSquelchOpen(false);
        v.pollOnce();
        assertTrue(v.message().get().startsWith("History error"), v.message().get());
        assertEquals(1, v.receptions().size(), "still shown");
    }

    @Test
    void exportReportsTheResult() {
        scanner.setSquelchOpen(true);
        vm.pollOnce();
        scanner.setSquelchOpen(false);
        vm.pollOnce();
        Path out = dir.resolve("export.xlsx");

        assertTrue(vm.export(List.copyOf(vm.receptions()), out));
        assertEquals(messages.get("history.exported", 1, "export.xlsx"), vm.message().get());
        assertTrue(Files.exists(out));

        assertFalse(vm.export(List.of(), dir.resolve("missing/export.xlsx")));
        assertTrue(vm.message().get().startsWith("History error"));
    }

    @Test
    void theSquelchIsPolledOnASchedule() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            scanner.setSquelchOpen(true);
            vm.startPolling(scheduler);
            vm.startPolling(scheduler); // restarting replaces the first schedule
            long deadline = System.currentTimeMillis() + 3_000;
            while (vm.receptions().isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertFalse(vm.receptions().isEmpty());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
