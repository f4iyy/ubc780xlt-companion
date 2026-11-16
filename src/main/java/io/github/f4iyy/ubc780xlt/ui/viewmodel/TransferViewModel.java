package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.transfer.MemoryReader;
import io.github.f4iyy.ubc780xlt.transfer.MemoryWriter;
import io.github.f4iyy.ubc780xlt.transfer.Progress;
import io.github.f4iyy.ubc780xlt.transfer.TransferCancelledException;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Download (read) and upload (write) of the whole scanner memory, with progress and cancellation. Everything that
 * polls the scanner is paused through {@code pause} while it runs.
 */
public final class TransferViewModel {

    private static final Logger LOG = LogManager.getLogger(TransferViewModel.class);

    private final ConnectionManager manager;
    private final DatabaseViewModel database;
    private final Consumer<Boolean> pause;
    private final Messages messages;
    private final Executor background;
    private final Executor fx;

    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty task = new SimpleStringProperty("");
    private final ObjectProperty<List<String>> warnings = new SimpleObjectProperty<>(List.of());
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private volatile boolean cancelled;

    public TransferViewModel(
            ConnectionManager manager,
            DatabaseViewModel database,
            Consumer<Boolean> pause,
            Messages messages,
            Executor background,
            Executor fx) {
        this.manager = manager;
        this.database = database;
        this.pause = pause;
        this.messages = messages;
        this.background = background;
        this.fx = fx;
        connected.set(manager.state() == ConnectionState.CONNECTED);
        manager.addListener(s -> fx.execute(() -> connected.set(s == ConnectionState.CONNECTED)));
    }

    public BooleanProperty running() {
        return running;
    }

    /** 0 to 1. */
    public DoubleProperty progress() {
        return progress;
    }

    /** What is being transferred, e.g. {@code Channel 42 (42/530)}. */
    public StringProperty task() {
        return task;
    }

    /** Warnings of the last transfer (answers the scanner refused or the program did not understand). */
    public ObjectProperty<List<String>> warnings() {
        return warnings;
    }

    /** True when connected and no transfer is running: the buttons can be used. */
    public BooleanBinding available() {
        return connected.and(running.not());
    }

    public void cancel() {
        cancelled = true;
    }

    /** Reads the whole memory from the scanner into the database. */
    public void download() {
        if (!begin()) {
            return;
        }
        background.execute(() -> {
            long start = System.nanoTime();
            MemoryReader reader = new MemoryReader(manager, reporter(), () -> cancelled);
            try {
                Programming read = reader.read();
                List<String> w = reader.warnings();
                fx.execute(() -> {
                    database.adoptDownloaded(read);
                    finish(w, messages.get("transfer.downloadDone", seconds(start), w.size()));
                });
            } catch (TransferCancelledException e) {
                fx.execute(() -> finish(reader.warnings(), messages.get("transfer.cancelled")));
            } catch (IOException e) {
                LOG.warn("Download failed: {}", e.getMessage());
                fx.execute(() -> finish(reader.warnings(), messages.get("transfer.failed", e.getMessage())));
            }
        });
    }

    /** Writes the whole database to the scanner memory. Refused if it contains values the scanner would refuse. */
    public void upload() {
        int issues = database.issues().size();
        if (issues > 0) {
            database.message().set(messages.get("transfer.invalid", issues));
            return;
        }
        if (!begin()) {
            return;
        }
        Programming snapshot = new Programming();
        snapshot.replaceWith(database.programming());
        background.execute(() -> {
            long start = System.nanoTime();
            MemoryWriter writer = new MemoryWriter(manager, reporter(), () -> cancelled);
            try {
                writer.write(snapshot);
                List<String> w = writer.warnings();
                fx.execute(() -> {
                    database.markTransferred();
                    finish(w, messages.get("transfer.uploadDone", seconds(start), w.size()));
                });
            } catch (TransferCancelledException e) {
                fx.execute(() -> finish(writer.warnings(), messages.get("transfer.cancelledWrite")));
            } catch (IOException e) {
                LOG.warn("Upload failed: {}", e.getMessage());
                fx.execute(() -> finish(writer.warnings(), messages.get("transfer.failed", e.getMessage())));
            }
        });
    }

    private boolean begin() {
        if (running.get() || !connected.get()) {
            database.message().set(messages.get("transfer.notConnected"));
            return false;
        }
        cancelled = false;
        running.set(true);
        progress.set(0);
        task.set("");
        warnings.set(List.of());
        pause.accept(true);
        return true;
    }

    private void finish(List<String> w, String message) {
        pause.accept(false);
        running.set(false);
        progress.set(0);
        task.set("");
        warnings.set(w);
        database.message().set(message);
    }

    private Progress reporter() {
        return (what, done, total) -> fx.execute(() -> {
            progress.set(total == 0 ? 0 : (double) done / total);
            task.set(messages.get("transfer.progress", what, done, total));
        });
    }

    private static String seconds(long startNanos) {
        return Long.toString((System.nanoTime() - startNanos) / 1_000_000_000L);
    }
}
