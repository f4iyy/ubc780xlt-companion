package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.protocol.LinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.ScannerDetector;
import io.github.f4iyy.ubc780xlt.protocol.SerialLink;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainViewModelTest {

    private static final ConnectionSettings FAST = new ConnectionSettings(50, 0);

    @TempDir
    Path dir;

    private AppConfig config;
    private Messages messages;

    @BeforeEach
    void setUp() {
        config = new AppConfig(dir.resolve("config.properties"));
        messages = Messages.of("en");
    }

    private MainViewModel viewModel(LinkFactory factory) {
        return new MainViewModel(
                new ConnectionManager(factory, FAST),
                new ScannerDetector(factory, FAST),
                factory, config, messages, Runnable::run, Runnable::run);
    }

    @Test
    void refreshPortsSelectsTheFirstPort() {
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("A", null).addPort("B", null));
        vm.refreshPorts();
        assertEquals(List.of("A", "B"), vm.ports());
        assertEquals("A", vm.selectedPort().get());
    }

    @Test
    void refreshPortsPrefersTheLastUsedPort() {
        config.setPort("B");
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("A", null).addPort("B", null));
        vm.refreshPorts();
        assertEquals("B", vm.selectedPort().get());
    }

    @Test
    void refreshPortsKeepsAValidSelection() {
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("A", null).addPort("B", null));
        vm.selectedPort().set("B");
        vm.refreshPorts();
        assertEquals("B", vm.selectedPort().get());
    }

    @Test
    void noPortsGivesAMessage() {
        MainViewModel vm = viewModel(new FakeLinkFactory());
        vm.selectedPort().set("GONE");
        vm.refreshPorts();
        assertEquals("", vm.selectedPort().get());
        assertEquals(messages.get("status.noPorts"), vm.message().get());
    }

    @Test
    void scanSelectsTheScannerPort() {
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("COM1", null).addPort("COM2", FakeScanner.bc780xlt()));
        vm.scan();
        assertEquals("COM2", vm.selectedPort().get());
        assertEquals(messages.get("status.found", "COM2", 9600), vm.message().get());
        assertFalse(vm.busy().get());
    }

    @Test
    void scanWithoutScannerSaysSo() {
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("COM1", null));
        vm.scan();
        assertEquals(messages.get("status.notFound"), vm.message().get());
        assertFalse(vm.busy().get());
    }

    @Test
    void connectWithoutAPortAsksForOne() {
        MainViewModel vm = viewModel(FakeLinkFactory.withSampleScanner());
        vm.connect();
        assertEquals(messages.get("status.noPort"), vm.message().get());
        assertFalse(vm.busy().get());
    }

    @Test
    void connectShowsTheStateTheScannerAndSavesTheConfig() {
        MainViewModel vm = viewModel(FakeLinkFactory.withSampleScanner());
        vm.selectedPort().set("SIM1");
        vm.connect();

        assertEquals(ConnectionState.CONNECTED, vm.state().get());
        assertEquals(messages.get("info.scanner", "BC780XLT", "1000000001", "103"), vm.scannerInfo().get());
        assertEquals(messages.get("status.connected", "SIM1", 9600), vm.message().get());
        assertFalse(vm.busy().get());

        AppConfig saved = AppConfig.load(dir.resolve("config.properties"));
        assertEquals("SIM1", saved.port());
        // The detected speed is not stored: "baud" stays on auto-detect.
        assertEquals(AppConfig.AUTO_BAUD, saved.baud());
    }

    @Test
    void disconnectClearsTheScannerInfo() {
        MainViewModel vm = viewModel(FakeLinkFactory.withSampleScanner());
        vm.selectedPort().set("SIM1");
        vm.connect();
        vm.disconnect();
        assertEquals(ConnectionState.DISCONNECTED, vm.state().get());
        assertEquals("", vm.scannerInfo().get());
    }

    @Test
    void connectToAnEmptyPortReportsNotFound() {
        MainViewModel vm = viewModel(new FakeLinkFactory().addPort("COM1", null));
        vm.selectedPort().set("COM1");
        vm.connect();
        assertEquals(messages.get("status.notFoundOnPort", "COM1", "9600, 19200, 4800, 2400"), vm.message().get());
        assertFalse(vm.busy().get());
        assertEquals(ConnectionState.DISCONNECTED, vm.state().get());
    }

    @Test
    void connectToAnotherModelReportsUnsupported() {
        MainViewModel vm = viewModel(new FakeLinkFactory()
                .addPort("COM1", new FakeScanner("BC245XLT", "1", "102", 9600)));
        vm.selectedPort().set("COM1");
        vm.connect();
        assertTrue(vm.message().get().contains("BC245XLT"));
    }

    @Test
    void otherErrorsAreShown() {
        FakeLinkFactory real = FakeLinkFactory.withSampleScanner();
        // The detection opens the port once and works; the real connection then fails to open it.
        LinkFactory failsOnSecondOpen = new LinkFactory() {
            private int opens;

            @Override
            public List<String> listPorts() {
                return real.listPorts();
            }

            @Override
            public SerialLink open(String port, int baud) throws IOException {
                if (++opens > 1) {
                    throw new IOException("boom");
                }
                return real.open(port, baud);
            }
        };
        MainViewModel vm = viewModel(failsOnSecondOpen);
        vm.selectedPort().set("SIM1");
        vm.connect();
        assertEquals(messages.get("status.error", "boom"), vm.message().get());
        assertEquals(ConnectionState.DISCONNECTED, vm.state().get());
    }
}
