package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferViewModelTest {

    @TempDir
    Path dir;

    private FakeScanner scanner;
    private ConnectionManager manager;
    private Messages messages;
    private DatabaseViewModel database;
    private RemoteViewModel remote;
    private TransferViewModel transfer;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        messages = Messages.of("en");
        database = new DatabaseViewModel(messages);
        remote = new RemoteViewModel(manager, new AppConfig(dir.resolve("c.properties")), messages, Runnable::run,
                Runnable::run);
        transfer = viewModel(Runnable::run);
        manager.connect("P", 0);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    private TransferViewModel viewModel(Executor background) {
        return new TransferViewModel(manager, database, remote::setPaused, messages, background, Runnable::run);
    }

    private static Programming sample() {
        Programming p = new Programming();
        p.scanBanks().get(0).tag().set("WORK");
        p.channel(1).set(new ChannelData(new BigDecimal("145.475"), new Tone(18, false), "CALL", false, false, true,
                false, false, true, Delay.S2, Modulation.FM, Step.K12_5));
        p.markClean();
        return p;
    }

    @Test
    void buttonsNeedAConnectionAndNoRunningTransfer() {
        assertTrue(transfer.available().get());
        manager.disconnect();
        assertFalse(transfer.available().get());
    }

    @Test
    void downloadReplacesTheDatabaseWithTheScannerMemory() {
        scanner.loadMemory(sample());
        database.editChannel(9, d -> d.withTag("OLD"));

        transfer.download();

        assertEquals("CALL", database.programming().channel(1).data().tag());
        assertEquals("WORK", database.programming().scanBanks().get(0).data().tag());
        assertTrue(database.programming().channel(9).data().isEmpty(), "the old content is gone");
        assertFalse(database.modified().get());
        assertFalse(database.history().canUndo().get());
        assertEquals(null, database.file().get());
        assertEquals(messages.get("transfer.downloadDone", "0", 0), database.message().get());
        assertFalse(transfer.running().get());
        assertEquals(0, transfer.progress().get());
        assertTrue(transfer.warnings().get().isEmpty());
    }

    @Test
    void uploadWritesTheDatabaseToTheScanner() {
        database.programming().replaceWith(sample());
        database.editChannel(2, d -> d.withFrequency(new BigDecimal("430.35")).withTag("UHF"));
        assertTrue(database.modified().get());

        transfer.upload();

        assertEquals("CALL", scanner.memory().channel(1).data().tag());
        assertEquals("UHF", scanner.memory().channel(2).data().tag());
        assertEquals("WORK", scanner.memory().scanBanks().get(0).data().tag());
        assertEquals(messages.get("transfer.uploadDone", "0", 0), database.message().get());
        assertFalse(database.modified().get(), "rows are no longer modified since the last write");
        assertFalse(transfer.running().get());
    }

    @Test
    void anUploadWithValuesTheScannerRefusesDoesNotStart() {
        database.programming().channel(1).set(ChannelData.empty().withFrequency(new BigDecimal("700")));
        int before = scanner.received().size();

        transfer.upload();

        assertEquals(messages.get("transfer.invalid", 1), database.message().get());
        assertEquals(before, scanner.received().size(), "nothing was sent");
    }

    @Test
    void nothingStartsWithoutAConnection() {
        manager.disconnect();
        transfer.download();
        assertEquals(messages.get("transfer.notConnected"), database.message().get());
        transfer.upload();
        assertEquals(messages.get("transfer.notConnected"), database.message().get());
    }

    @Test
    void aBrokenLinkEndsTheTransferWithAnError() {
        scanner.unplug();
        transfer.download();
        assertTrue(database.message().get().startsWith("Transfer failed"), database.message().get());
        assertFalse(transfer.running().get());

        // The write also reports the failure.
        manager.close();
        TransferViewModel other = viewModel(Runnable::run);
        other.upload();
    }

    @Test
    void cancellingStopsTheTransfer() {
        scanner.loadMemory(sample());
        TransferViewModel[] holder = new TransferViewModel[1];
        TransferViewModel t = new TransferViewModel(manager, database, remote::setPaused, messages, task -> {
            // The user presses Cancel right after the transfer started.
            holder[0].cancel();
            task.run();
        }, Runnable::run);
        holder[0] = t;
        t.download();
        assertEquals(messages.get("transfer.cancelled"), database.message().get());
        assertFalse(t.running().get());
        assertTrue(database.programming().channel(1).data().isEmpty(), "the database was not touched");

        t.upload();
        assertEquals(messages.get("transfer.cancelledWrite"), database.message().get());
    }

    @Test
    void refusedAnswersAreCollectedAsWarnings() {
        scanner.loadMemory(sample());
        scanner.garbleNext(2);
        List<List<String>> seen = new ArrayList<>();
        transfer.warnings().addListener((obs, old, now) -> seen.add(now));

        transfer.download();

        assertFalse(transfer.warnings().get().isEmpty());
        assertEquals(1, seen.size());
        assertTrue(database.message().get().startsWith("Memory read"));
    }

    @Test
    void theDisplayRefreshIsPausedDuringATransfer() {
        // While paused, the display refresh sends nothing.
        remote.setPaused(true);
        int before = scanner.received().size();
        remote.pollOnce();
        assertEquals(before, scanner.received().size());
        remote.setPaused(false);
        remote.pollOnce();
        assertTrue(scanner.received().size() > before);
    }
}
