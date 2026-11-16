package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.protocol.DetectedScanner;
import io.github.f4iyy.ubc780xlt.protocol.LinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.ScannerDetector;
import io.github.f4iyy.ubc780xlt.protocol.ScannerInfo;
import io.github.f4iyy.ubc780xlt.protocol.ScannerNotFoundException;
import io.github.f4iyy.ubc780xlt.protocol.UnsupportedScannerException;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State and actions of the main window: port list, scan, connect/disconnect, status.
 * Slow work runs on {@code background}; results are published with {@code fx}.
 */
public final class MainViewModel {

    private static final Logger LOG = LogManager.getLogger(MainViewModel.class);

    private final ConnectionManager manager;
    private final ScannerDetector detector;
    private final LinkFactory factory;
    private final AppConfig config;
    private final Messages messages;
    private final Executor background;
    private final Executor fx;

    private final ObservableList<String> ports = FXCollections.observableArrayList();
    private final StringProperty selectedPort = new SimpleStringProperty("");
    private final ObjectProperty<ConnectionState> state = new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);
    private final StringProperty message = new SimpleStringProperty("");
    private final StringProperty scannerInfo = new SimpleStringProperty("");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    public MainViewModel(
            ConnectionManager manager,
            ScannerDetector detector,
            LinkFactory factory,
            AppConfig config,
            Messages messages,
            Executor background,
            Executor fx) {
        this.manager = manager;
        this.detector = detector;
        this.factory = factory;
        this.config = config;
        this.messages = messages;
        this.background = background;
        this.fx = fx;
        manager.addListener(s -> fx.execute(() -> onState(s)));
    }

    public ObservableList<String> ports() {
        return ports;
    }

    public StringProperty selectedPort() {
        return selectedPort;
    }

    public ObjectProperty<ConnectionState> state() {
        return state;
    }

    public StringProperty message() {
        return message;
    }

    public StringProperty scannerInfo() {
        return scannerInfo;
    }

    public BooleanProperty busy() {
        return busy;
    }

    /** Reloads the port list, keeping the selection, else the last used port, else the first one. */
    public void refreshPorts() {
        List<String> found = factory.listPorts();
        ports.setAll(found);
        if (found.isEmpty()) {
            selectedPort.set("");
            message.set(messages.get("status.noPorts"));
        } else if (!found.contains(selectedPort.get())) {
            selectedPort.set(found.contains(config.port()) ? config.port() : found.get(0));
        }
    }

    /** Probes every port with {@code SI}; selects the scanner when exactly one is found. */
    public void scan() {
        busy.set(true);
        background.execute(() -> {
            List<DetectedScanner> found = detector.scanAll(
                    port -> fx.execute(() -> message.set(messages.get("status.scanning", port))));
            fx.execute(() -> {
                busy.set(false);
                if (found.isEmpty()) {
                    message.set(messages.get("status.notFound"));
                    return;
                }
                DetectedScanner first = found.get(0);
                selectedPort.set(first.port());
                message.set(messages.get("status.found", first.port(), first.baud()));
            });
        });
    }

    public void connect() {
        String port = selectedPort.get();
        if (port == null || port.isBlank()) {
            message.set(messages.get("status.noPort"));
            return;
        }
        busy.set(true);
        message.set(messages.get("status.connecting", port));
        background.execute(() -> {
            try {
                manager.connect(port, config.baud());
                fx.execute(() -> onConnected(port));
            } catch (IOException e) {
                LOG.warn("Connection to {} failed: {}", port, e.getMessage());
                fx.execute(() -> onConnectFailed(port, e));
            }
        });
    }

    public void disconnect() {
        background.execute(manager::disconnect);
    }

    private void onConnected(String port) {
        busy.set(false);
        message.set(messages.get("status.connected", port, manager.baud()));
        // Only the port is remembered: "baud" stays the user's choice (0 = auto-detect).
        config.setPort(port);
        try {
            config.save();
        } catch (IOException e) {
            LOG.warn("Cannot save the configuration: {}", e.getMessage());
        }
    }

    private void onConnectFailed(String port, IOException e) {
        busy.set(false);
        if (e instanceof ScannerNotFoundException notFound) {
            message.set(messages.get("status.notFoundOnPort", port, notFound.speeds()));
        } else if (e instanceof UnsupportedScannerException) {
            message.set(messages.get("status.unsupported", e.getMessage()));
        } else {
            message.set(messages.get("status.error", e.getMessage()));
        }
    }

    private void onState(ConnectionState newState) {
        state.set(newState);
        scannerInfo.set(newState == ConnectionState.CONNECTED
                ? manager.info().map(this::describe).orElse("")
                : "");
    }

    private String describe(ScannerInfo info) {
        return messages.get("info.scanner", info.model(), info.serial(), info.commandVersion());
    }
}
