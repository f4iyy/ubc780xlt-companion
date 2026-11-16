package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.history.HistoryExport;
import io.github.f4iyy.ubc780xlt.history.HistoryLog;
import io.github.f4iyy.ubc780xlt.history.Reception;
import io.github.f4iyy.ubc780xlt.history.ReceptionTracker;
import io.github.f4iyy.ubc780xlt.history.ScannerSource;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The history tab: what the scanner receives. The squelch is looked at every {@code historyPollMs}; each reception
 * is shown live and appended to the daily CSV file.
 */
public final class HistoryViewModel {

    private static final Logger LOG = LogManager.getLogger(HistoryViewModel.class);

    private final ConnectionManager manager;
    private final AppConfig config;
    private final HistoryLog log;
    private final Messages messages;
    private final Executor fx;
    private final ZoneId zone;
    private final ReceptionTracker tracker;

    private final ObservableList<Reception> receptions = FXCollections.observableArrayList();
    private final BooleanProperty recording;
    private final StringProperty message = new SimpleStringProperty("");
    private volatile boolean paused;
    private ScheduledFuture<?> polling;

    public HistoryViewModel(
            ConnectionManager manager,
            AppConfig config,
            HistoryLog log,
            Messages messages,
            Executor fx,
            Clock clock) {
        this.manager = manager;
        this.config = config;
        this.log = log;
        this.messages = messages;
        this.fx = fx;
        this.zone = clock.getZone();
        this.recording = new SimpleBooleanProperty(config.history());
        this.tracker = new ReceptionTracker(new ScannerSource(manager), clock, new ReceptionTracker.Listener() {
            @Override
            public void started(Reception r) {
                fx.execute(() -> receptions.add(0, r));
            }

            @Override
            public void finished(Reception r) {
                onFinished(r);
            }
        });
        recording.addListener((obs, old, now) -> {
            config.setHistory(now);
            try {
                config.save();
            } catch (IOException e) {
                LOG.warn("Cannot save the configuration: {}", e.getMessage());
            }
        });
    }

    /** Newest first. */
    public ObservableList<Reception> receptions() {
        return receptions;
    }

    /** Whether receptions are looked for and recorded. */
    public BooleanProperty recording() {
        return recording;
    }

    public StringProperty message() {
        return message;
    }

    /** Stops (or resumes) the squelch polling, e.g. during a memory transfer. */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Looks at the squelch every {@code AppConfig.historyPollMs()}. */
    public synchronized void startPolling(ScheduledExecutorService scheduler) {
        if (polling != null) {
            polling.cancel(false);
        }
        polling = scheduler.scheduleWithFixedDelay(this::pollOnce, 0, config.historyPollMs(), TimeUnit.MILLISECONDS);
    }

    /** One look at the scanner. */
    public void pollOnce() {
        if (!recording.get() || paused || manager.state() != ConnectionState.CONNECTED) {
            tracker.abort();
            return;
        }
        try {
            tracker.poll();
        } catch (IOException e) {
            LOG.debug("History poll failed: {}", e.getMessage());
            tracker.abort();
        } catch (RuntimeException e) {
            LOG.error("History poll crashed", e);
            tracker.abort();
        }
    }

    private void onFinished(Reception r) {
        try {
            log.append(r);
        } catch (IOException e) {
            LOG.warn("Cannot write the history file: {}", e.getMessage());
            fx.execute(() -> message.set(messages.get("history.writeError", e.getMessage())));
        }
        fx.execute(() -> {
            for (int i = 0; i < receptions.size(); i++) {
                Reception existing = receptions.get(i);
                if (existing.inProgress() && existing.start().equals(r.start())) {
                    receptions.set(i, r);
                    return;
                }
            }
            receptions.add(0, r);
        });
    }

    public void clear() {
        receptions.removeIf(r -> !r.inProgress());
    }

    /** Writes the given receptions to an XLSX workbook. @return true if written */
    public boolean export(List<Reception> selection, Path file) {
        try {
            HistoryExport.write(selection, file, zone);
            message.set(messages.get("history.exported", selection.size(), file.getFileName()));
            return true;
        } catch (IOException e) {
            message.set(messages.get("history.writeError", e.getMessage()));
            return false;
        }
    }
}
