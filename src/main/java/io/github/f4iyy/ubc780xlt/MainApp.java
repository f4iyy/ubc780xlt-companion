package io.github.f4iyy.ubc780xlt;

import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.LinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.ScannerDetector;
import io.github.f4iyy.ubc780xlt.protocol.serial.JSerialCommFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.history.HistoryLog;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.view.MainView;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.DatabaseViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.HistoryViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.MainViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.RemoteViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.SettingsViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.TransferViewModel;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JavaFX application. Start with {@code --simulate} to use a simulated BC780XLT on port {@code SIM1}
 * instead of real serial ports.
 */
public class MainApp extends Application {

    private static final Logger LOG = LogManager.getLogger(MainApp.class);

    private ConnectionManager manager;
    private ExecutorService background;
    private ExecutorService transferExecutor;
    private ScheduledExecutorService scheduler;
    private ScheduledExecutorService pollScheduler;
    private ScheduledExecutorService historyScheduler;
    private RemoteViewModel remote;
    private MainView view;

    @Override
    public void start(Stage stage) {
        boolean simulate = getParameters().getRaw().contains("--simulate");
        AppConfig config = AppConfig.load(AppPaths.configFile());
        Messages messages = Messages.of(config.language());
        LOG.info("Starting {} {} (simulate={})", AppInfo.NAME, AppInfo.version(), simulate);

        LinkFactory factory = simulate ? FakeLinkFactory.withSampleScanner() : new JSerialCommFactory();
        ConnectionSettings settings = ConnectionSettings.defaults();
        manager = new ConnectionManager(factory, settings);
        ScannerDetector detector = new ScannerDetector(factory, settings);

        background = Executors.newSingleThreadExecutor(r -> daemon(r, "background"));
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "keep-alive"));
        manager.start(scheduler, config.reconnectSeconds() * 1000L);

        MainViewModel vm = new MainViewModel(
                manager, detector, factory, config, messages, background, Platform::runLater);
        vm.refreshPorts();

        remote = new RemoteViewModel(manager, config, messages, background, Platform::runLater);
        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "display-poll"));
        remote.startPolling(pollScheduler);

        DatabaseViewModel database = new DatabaseViewModel(messages);
        transferExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "transfer"));
        HistoryViewModel history = new HistoryViewModel(manager, config,
                new HistoryLog(AppPaths.logDir(), ZoneId.systemDefault()), messages, Platform::runLater,
                Clock.systemDefaultZone());
        historyScheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "history-poll"));
        history.startPolling(historyScheduler);
        SettingsViewModel scannerSettings = new SettingsViewModel(manager, messages, background, Platform::runLater);
        TransferViewModel transfer = new TransferViewModel(manager, database, paused -> {
            remote.setPaused(paused);
            history.setPaused(paused);
        }, messages, transferExecutor, Platform::runLater);

        view = new MainView(vm, remote, database, transfer, history, scannerSettings, messages, config);
        view.show(stage);
    }

    @Override
    public void stop() {
        if (view != null) {
            view.dispose();
        }
        if (pollScheduler != null) {
            pollScheduler.shutdownNow();
        }
        if (historyScheduler != null) {
            historyScheduler.shutdownNow();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (manager != null) {
            manager.close();
        }
        if (transferExecutor != null) {
            transferExecutor.shutdownNow();
        }
        if (background != null) {
            background.shutdownNow();
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
