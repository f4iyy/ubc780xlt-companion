package io.github.f4iyy.ubc780xlt.protocol;

import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdParser;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdState;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owns the connection to the scanner: connects (with speed auto-detection), checks it is a BC780XLT,
 * keeps the link alive and reconnects when it breaks.
 *
 * <p>States: DISCONNECTED → CONNECTING → CONNECTED → (link breaks) LOST → RECONNECTING → CONNECTED.
 * {@link #tick()} drives the heartbeat and the reconnection; {@link #start} calls it periodically.
 */
public final class ConnectionManager implements AutoCloseable, MultiLineSender {

    private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);

    /** Auto-generating commands must be OFF, or the scanner floods the line (Uniden programmer's note). */
    private static final List<String> AUTO_GENERATING_OFF = List.of("IDF", "QUF", "RIF");

    private final LinkFactory factory;
    private final ConnectionSettings settings;
    private final ScannerDetector detector;
    private final List<Consumer<ConnectionState>> listeners = new CopyOnWriteArrayList<>();

    private ConnectionState state = ConnectionState.DISCONNECTED;
    private ScannerConnection connection;
    private ScannerInfo info;
    private String port;
    private int baud;
    private boolean reconnectWanted;

    public ConnectionManager(LinkFactory factory, ConnectionSettings settings) {
        this.factory = factory;
        this.settings = settings;
        this.detector = new ScannerDetector(factory, settings);
    }

    public void addListener(Consumer<ConnectionState> listener) {
        listeners.add(listener);
    }

    public synchronized ConnectionState state() {
        return state;
    }

    public synchronized Optional<ScannerInfo> info() {
        return Optional.ofNullable(info);
    }

    /** Speed of the current (or last) connection, 0 if none yet. */
    public synchronized int baud() {
        return baud;
    }

    public synchronized String port() {
        return port;
    }

    /**
     * Connects to the scanner on {@code port}.
     *
     * @param baudOrAuto the speed, or {@link io.github.f4iyy.ubc780xlt.AppConfig#AUTO_BAUD} (0) to detect it
     * @throws ScannerNotFoundException when nothing answers
     * @throws UnsupportedScannerException when the device is not a BC780XLT
     */
    public synchronized void connect(String port, int baudOrAuto) throws IOException {
        closeConnection();
        LOG.info("Connecting to {} ({})", port, baudOrAuto == 0 ? "auto-detecting the speed" : baudOrAuto + " baud");
        setState(ConnectionState.CONNECTING);
        try {
            Optional<DetectedScanner> found = baudOrAuto == 0
                    ? detector.probe(port)
                    : detector.identify(port, baudOrAuto);
            int[] tried = baudOrAuto == 0 ? ScannerDetector.BAUDS : new int[] {baudOrAuto};
            DetectedScanner scanner = found.orElseThrow(() -> new ScannerNotFoundException(port, tried));
            if (!scanner.isSupported()) {
                throw new UnsupportedScannerException(scanner.info().model());
            }
            open(scanner);
        } catch (IOException e) {
            setState(ConnectionState.DISCONNECTED);
            throw e;
        }
    }

    /** Switches the auto-generating commands off, closes the link and stops any reconnection. */
    public synchronized void disconnect() {
        reconnectWanted = false;
        if (connection != null) {
            switchAutoGeneratingOff();
        }
        closeConnection();
        setState(ConnectionState.DISCONNECTED);
    }

    /**
     * Sends a command on the live connection. A broken link moves the state to LOST.
     *
     * @throws IOException if not connected, on timeout, or if the link is broken
     */
    @Override
    public synchronized Response send(String command) throws IOException {
        if (state != ConnectionState.CONNECTED) {
            throw new IOException("Not connected");
        }
        try {
            return connection.send(command);
        } catch (ScannerTimeoutException e) {
            throw e;
        } catch (IOException e) {
            onLinkFailure(e);
            throw e;
        }
    }

    /** Sends a command with a multi-line answer. Same failure handling as {@link #send}. */
    @Override
    public synchronized List<String> sendLines(String command, int lineCount) throws IOException {
        if (state != ConnectionState.CONNECTED) {
            throw new IOException("Not connected");
        }
        try {
            return connection.sendLines(command, lineCount);
        } catch (ScannerTimeoutException e) {
            throw e;
        } catch (IOException e) {
            onLinkFailure(e);
            throw e;
        }
    }

    /** Reads the whole display with the {@code LCD} command. */
    public LcdState readLcd() throws IOException {
        return LcdParser.parse(sendLines("LCD", LcdParser.LINE_COUNT));
    }

    /**
     * Presses (or holds) a front-panel key.
     *
     * @throws IOException if the scanner does not answer {@code OK} (except when it is not expected to answer)
     */
    public void pressKey(ScannerKey key, boolean hold) throws IOException {
        try {
            Response r = send(key.command(hold));
            if (!r.isOk()) {
                throw new IOException("Key " + key + " refused: " + r.text());
            }
        } catch (ScannerTimeoutException e) {
            if (key.answers(hold)) {
                throw e;
            }
        }
    }

    /** One step of the keep-alive: heartbeat when connected, reconnection attempt when the link was lost. */
    public synchronized void tick() {
        switch (state) {
            case CONNECTED -> {
                try {
                    connection.send("SI");
                } catch (IOException e) {
                    onLinkFailure(e);
                }
            }
            case LOST, RECONNECTING -> {
                if (reconnectWanted) {
                    tryReconnect();
                }
            }
            default -> {
                // nothing to do
            }
        }
    }

    /** Calls {@link #tick()} every {@code periodMs} on {@code scheduler}. */
    public void start(ScheduledExecutorService scheduler, long periodMs) {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                tick();
            } catch (RuntimeException e) {
                LOG.error("Keep-alive failed", e);
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        disconnect();
    }

    private void tryReconnect() {
        setState(ConnectionState.RECONNECTING);
        Optional<DetectedScanner> found = detector.identify(port, baud).filter(DetectedScanner::isSupported);
        if (found.isEmpty()) {
            LOG.debug("Reconnection to {} failed, will retry", port);
            return;
        }
        try {
            open(found.get());
            LOG.info("Reconnected to {}", port);
        } catch (IOException e) {
            LOG.debug("Reconnection to {} failed: {}", port, e.getMessage());
        }
    }

    private void open(DetectedScanner scanner) throws IOException {
        SerialLink link = factory.open(scanner.port(), scanner.baud());
        connection = new ScannerConnection(link, settings);
        info = scanner.info();
        port = scanner.port();
        baud = scanner.baud();
        reconnectWanted = true;
        switchAutoGeneratingOff();
        LOG.info("Connected to {} on {} at {} baud", info.model(), port, baud);
        setState(ConnectionState.CONNECTED);
    }

    private void switchAutoGeneratingOff() {
        for (String command : AUTO_GENERATING_OFF) {
            try {
                connection.send(command);
            } catch (IOException e) {
                LOG.warn("Could not send {}: {}", command, e.getMessage());
            }
        }
    }

    private void onLinkFailure(IOException cause) {
        LOG.warn("Link to {} lost: {}", port, cause.getMessage());
        closeConnection();
        setState(ConnectionState.LOST);
    }

    private void closeConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void setState(ConnectionState newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        for (Consumer<ConnectionState> l : listeners) {
            l.accept(newState);
        }
    }
}
