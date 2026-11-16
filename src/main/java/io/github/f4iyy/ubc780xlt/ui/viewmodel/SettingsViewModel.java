package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.protocol.FrequencyRanges;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.Backlight;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.CtcssFunction;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.MuteMode;
import io.github.f4iyy.ubc780xlt.settings.SettingsClient;
import io.github.f4iyy.ubc780xlt.settings.SettingsClient.Outcome;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The scanner settings tab. Values are read when asked for (and at connection); every change is written to the
 * scanner at once. A change the scanner refuses (typically: not allowed in the current mode) is undone.
 */
public final class SettingsViewModel {

    @FunctionalInterface
    private interface Write {
        Outcome run() throws IOException;
    }

    private static final Logger LOG = LogManager.getLogger(SettingsViewModel.class);

    private final SettingsClient client;
    private final Messages messages;
    private final Executor background;
    private final Executor fx;

    private final ObjectProperty<Backlight> backlight = new SimpleObjectProperty<>(Backlight.NORMAL);
    private final BooleanProperty beep = new SimpleBooleanProperty();
    private final BooleanProperty priority = new SimpleBooleanProperty();
    private final BooleanProperty dataSkip = new SimpleBooleanProperty();
    private final ObjectProperty<MuteMode> mute = new SimpleObjectProperty<>(MuteMode.AUTO);
    private final BooleanProperty editLock = new SimpleBooleanProperty();
    private final BooleanProperty screenMask = new SimpleBooleanProperty();
    private final ObjectProperty<CtcssFunction> ctcss = new SimpleObjectProperty<>(CtcssFunction.OFF);
    private final BooleanProperty[] scanBanks = flags(10);
    private final BooleanProperty[] searchBanks = flags(10);
    private final ObservableList<String> skipList = FXCollections.observableArrayList();
    private final StringProperty message = new SimpleStringProperty("");
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private boolean applying;

    public SettingsViewModel(
            ConnectionManager manager, Messages messages, Executor background, Executor fx) {
        this(manager, manager, messages, background, fx);
    }

    SettingsViewModel(CommandSender sender, ConnectionManager manager, Messages messages, Executor background,
            Executor fx) {
        this.client = new SettingsClient(sender);
        this.messages = messages;
        this.background = background;
        this.fx = fx;
        connected.set(manager.state() == ConnectionState.CONNECTED);
        manager.addListener(s -> fx.execute(() -> {
            boolean ok = s == ConnectionState.CONNECTED;
            connected.set(ok);
            if (ok) {
                load();
            }
        }));
        track(backlight, "settings.backlight", v -> () -> client.setBacklight(v));
        track(beep, "settings.beep", v -> () -> client.setBeep(v));
        track(priority, "settings.priority", v -> () -> client.setPriority(v));
        track(dataSkip, "settings.dataSkip", v -> () -> client.setDataSkip(v));
        track(mute, "settings.mute", v -> () -> client.setMute(v));
        track(editLock, "settings.editLock", v -> () -> client.setEditLock(v));
        track(screenMask, "settings.screenMask", v -> () -> client.setScreenMask(v));
        track(ctcss, "settings.ctcss", v -> () -> client.setCtcss(v));
        for (BooleanProperty b : scanBanks) {
            trackBanks(b, scanBanks, true);
        }
        for (BooleanProperty b : searchBanks) {
            trackBanks(b, searchBanks, false);
        }
    }

    private static BooleanProperty[] flags(int count) {
        BooleanProperty[] array = new BooleanProperty[count];
        for (int i = 0; i < count; i++) {
            array[i] = new SimpleBooleanProperty();
        }
        return array;
    }

    public ObjectProperty<Backlight> backlight() {
        return backlight;
    }

    public BooleanProperty beep() {
        return beep;
    }

    public BooleanProperty priority() {
        return priority;
    }

    public BooleanProperty dataSkip() {
        return dataSkip;
    }

    public ObjectProperty<MuteMode> mute() {
        return mute;
    }

    public BooleanProperty editLock() {
        return editLock;
    }

    public BooleanProperty screenMask() {
        return screenMask;
    }

    public ObjectProperty<CtcssFunction> ctcss() {
        return ctcss;
    }

    /** @param bank 1 to 10 */
    public BooleanProperty scanBank(int bank) {
        return scanBanks[bank - 1];
    }

    /** @param bank 1 to 10 */
    public BooleanProperty searchBank(int bank) {
        return searchBanks[bank - 1];
    }

    public ObservableList<String> skipList() {
        return skipList;
    }

    public StringProperty message() {
        return message;
    }

    public BooleanProperty connected() {
        return connected;
    }

    /** Reads every setting from the scanner. */
    public void load() {
        background.execute(() -> {
            try {
                SettingsClient.ReadResult result = client.read();
                fx.execute(() -> show(result));
            } catch (IOException e) {
                LOG.warn("Cannot read the settings: {}", e.getMessage());
                fx.execute(() -> message.set(messages.get("settings.error", e.getMessage())));
            }
        });
    }

    private void show(SettingsClient.ReadResult r) {
        ScannerSettings s = r.settings();
        applying = true;
        try {
            backlight.set(s.backlight());
            beep.set(s.beep());
            priority.set(s.priority());
            dataSkip.set(s.dataSkip());
            mute.set(s.mute());
            editLock.set(s.editLock());
            screenMask.set(s.screenMask());
            ctcss.set(s.ctcss());
            for (int i = 0; i < 10; i++) {
                scanBanks[i].set(s.scanBanks().contains(i + 1));
                searchBanks[i].set(s.searchBanks().contains(i + 1));
            }
            skipList.setAll(s.skipList().stream().map(f -> f.setScale(4).toPlainString()).toList());
        } finally {
            applying = false;
        }
        message.set(r.warnings().isEmpty()
                ? messages.get("settings.loaded")
                : messages.get("settings.loadedWithWarnings", r.warnings().size()));
    }

    /** Adds a frequency (MHz) to the search skip memory; the scanner cannot remove one. */
    public void addSkip(String mhzText) {
        var parsed = FrequencyRanges.parseMhz(mhzText);
        if (parsed.isEmpty() || !FrequencyRanges.isValid(parsed.get())) {
            message.set(messages.get("tune.invalid"));
            return;
        }
        BigDecimal f = parsed.get();
        if (skipList.size() >= ScannerSettings.SKIP_CAPACITY) {
            message.set(messages.get("settings.skipFull"));
            return;
        }
        background.execute(() -> {
            try {
                Outcome o = client.addSkipFrequency(f);
                fx.execute(() -> {
                    if (o == Outcome.OK) {
                        String text = f.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
                        if (!skipList.contains(text)) {
                            skipList.add(text);
                        }
                        message.set(messages.get("settings.skipAdded", text));
                    } else {
                        message.set(messages.get("settings.refused", messages.get("settings.skip")));
                    }
                });
            } catch (IOException e) {
                fx.execute(() -> message.set(messages.get("settings.error", e.getMessage())));
            }
        });
    }

    // ---- writing on change ------------------------------------------------------------------

    /** Writes each change; undoes it if the scanner refuses. */
    private <T> void track(Property<T> property, String nameKey, java.util.function.Function<T, Write> writer) {
        property.addListener((obs, old, now) -> {
            if (applying || !connected.get()) {
                return;
            }
            send(nameKey, writer.apply(now), () -> set(property, old));
        });
    }

    private void trackBanks(BooleanProperty changed, BooleanProperty[] group, boolean scan) {
        changed.addListener((obs, old, now) -> {
            if (applying || !connected.get()) {
                return;
            }
            Set<Integer> selected = new TreeSet<>();
            for (int i = 0; i < group.length; i++) {
                if (group[i].get()) {
                    selected.add(i + 1);
                }
            }
            if (selected.isEmpty()) {
                set(changed, old);
                message.set(messages.get("settings.oneBank"));
                return;
            }
            send(scan ? "settings.scanBanks" : "settings.searchBanks",
                    () -> scan ? client.setScanBanks(selected) : client.setSearchBanks(selected),
                    () -> set(changed, old));
        });
    }

    private <T> void set(Property<T> property, T value) {
        applying = true;
        try {
            property.setValue(value);
        } finally {
            applying = false;
        }
    }

    private void send(String nameKey, Write write, Runnable undo) {
        background.execute(() -> {
            try {
                Outcome o = write.run();
                fx.execute(() -> {
                    if (o == Outcome.OK) {
                        message.set(messages.get("settings.saved", messages.get(nameKey)));
                    } else {
                        undo.run();
                        message.set(messages.get(o == Outcome.NOT_NOW ? "settings.notNow" : "settings.refused",
                                messages.get(nameKey)));
                    }
                });
            } catch (IOException e) {
                LOG.warn("Cannot write a setting: {}", e.getMessage());
                fx.execute(() -> {
                    undo.run();
                    message.set(messages.get("settings.error", e.getMessage()));
                });
            }
        });
    }
}
