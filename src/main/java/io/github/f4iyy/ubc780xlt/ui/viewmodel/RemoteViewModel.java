package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.protocol.FrequencyRanges;
import io.github.f4iyy.ubc780xlt.protocol.ScannerKey;
import io.github.f4iyy.ubc780xlt.protocol.ScannerTimeoutException;
import io.github.f4iyy.ubc780xlt.protocol.TuneService;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdState;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Remote control: the replicated display (polled), the keypad and the "Frequency / Tune" box. */
public final class RemoteViewModel {

    private static final Logger LOG = LogManager.getLogger(RemoteViewModel.class);

    private final ConnectionManager manager;
    private final AppConfig config;
    private final Messages messages;
    private final TuneService tuneService;
    private final Executor background;
    private final Executor fx;

    private final ObjectProperty<LcdState> lcd = new SimpleObjectProperty<>(LcdState.blank());
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty tuneMessage = new SimpleStringProperty("");

    private volatile boolean paused;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> polling;

    public RemoteViewModel(
            ConnectionManager manager,
            AppConfig config,
            Messages messages,
            Executor background,
            Executor fx) {
        this.manager = manager;
        this.config = config;
        this.messages = messages;
        this.tuneService = new TuneService(manager);
        this.background = background;
        this.fx = fx;
        manager.addListener(s -> fx.execute(() -> onState(s)));
    }

    public ObjectProperty<LcdState> lcd() {
        return lcd;
    }

    public BooleanProperty connected() {
        return connected;
    }

    public StringProperty tuneMessage() {
        return tuneMessage;
    }

    /** Stops (or resumes) the display refresh, e.g. while the scanner memory is being transferred. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Starts the periodic display refresh, every {@code AppConfig.pollingMs()}. */
    public synchronized void startPolling(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        restartPolling();
    }

    /** Applies a new refresh interval (100 to 2000 ms). */
    public synchronized void setPollingMs(int ms) {
        config.setPollingMs(ms);
        if (scheduler != null) {
            restartPolling();
        }
    }

    private void restartPolling() {
        if (polling != null) {
            polling.cancel(false);
        }
        polling = scheduler.scheduleWithFixedDelay(this::pollOnce, 0, config.pollingMs(), TimeUnit.MILLISECONDS);
    }

    /** One refresh of the display; does nothing when not connected. */
    void pollOnce() {
        if (paused || manager.state() != ConnectionState.CONNECTED) {
            return;
        }
        try {
            LcdState state = manager.readLcd();
            fx.execute(() -> lcd.set(state));
        } catch (ScannerTimeoutException e) {
            LOG.debug("Display refresh timed out");
        } catch (IOException e) {
            LOG.debug("Display refresh failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Display refresh crashed", e);
        }
    }

    /** Presses a key; {@code hold} is a long press. Runs in the background. */
    public void pressKey(ScannerKey key, boolean hold) {
        background.execute(() -> {
            try {
                manager.pressKey(key, hold);
            } catch (IOException e) {
                LOG.warn("Key {} failed: {}", key, e.getMessage());
            }
        });
    }

    /**
     * Tunes to the frequency typed by the user.
     *
     * @param mhzText frequency in MHz, as typed
     */
    public void tune(String mhzText, Step step, Modulation modulation) {
        Optional<BigDecimal> parsed = FrequencyRanges.parseMhz(mhzText);
        if (parsed.isEmpty()) {
            tuneMessage.set(messages.get("tune.invalid"));
            return;
        }
        BigDecimal mhz = parsed.get();
        if (!FrequencyRanges.isValid(mhz)) {
            tuneMessage.set(messages.get("tune.outOfRange", mhz.stripTrailingZeros().toPlainString()));
            return;
        }
        tuneMessage.set("");
        background.execute(() -> {
            try {
                tuneService.tune(mhz, step, modulation);
                fx.execute(() -> tuneMessage.set(messages.get("tune.done", mhz.stripTrailingZeros().toPlainString())));
            } catch (IOException e) {
                LOG.warn("Tune to {} failed: {}", mhz, e.getMessage());
                fx.execute(() -> tuneMessage.set(messages.get("tune.error", e.getMessage())));
            }
        });
    }

    private void onState(ConnectionState state) {
        boolean ok = state == ConnectionState.CONNECTED;
        connected.set(ok);
        if (!ok) {
            lcd.set(LcdState.blank());
        }
    }
}
