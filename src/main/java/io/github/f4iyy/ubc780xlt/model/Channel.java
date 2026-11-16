package io.github.f4iyy.ubc780xlt.model;

import java.math.BigDecimal;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** One of the 500 channels, with observable properties for the tables. */
public final class Channel {

    private final int number;
    private final ObjectProperty<BigDecimal> frequency = new SimpleObjectProperty<>();
    private final ObjectProperty<Tone> tone = new SimpleObjectProperty<>();
    private final StringProperty tag = new SimpleStringProperty();
    private final BooleanProperty att = new SimpleBooleanProperty();
    private final BooleanProperty lockout = new SimpleBooleanProperty();
    private final BooleanProperty beep = new SimpleBooleanProperty();
    private final BooleanProperty rec = new SimpleBooleanProperty();
    private final BooleanProperty trunk = new SimpleBooleanProperty();
    private final BooleanProperty priority = new SimpleBooleanProperty();
    private final ObjectProperty<Delay> delay = new SimpleObjectProperty<>();
    private final ObjectProperty<Modulation> modulation = new SimpleObjectProperty<>();
    private final ObjectProperty<Step> step = new SimpleObjectProperty<>();

    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(false);
    private ChannelData baseline = ChannelData.empty();

    public Channel(int number) {
        this.number = number;
        set(ChannelData.empty());
        for (javafx.beans.value.ObservableValue<?> p : new javafx.beans.value.ObservableValue<?>[] {
                frequency, tone, tag, att, lockout, beep, rec, trunk, priority, delay, modulation, step}) {
            p.addListener((obs, old, now) -> modified.set(!data().equals(baseline)));
        }
    }

    public int number() {
        return number;
    }

    /** Bank number 1 to 10. */
    public int bank() {
        return (number - 1) / Programming.CHANNELS_PER_BANK + 1;
    }

    public ObjectProperty<BigDecimal> frequency() {
        return frequency;
    }

    public ObjectProperty<Tone> tone() {
        return tone;
    }

    public StringProperty tag() {
        return tag;
    }

    public BooleanProperty att() {
        return att;
    }

    public BooleanProperty lockout() {
        return lockout;
    }

    public BooleanProperty beep() {
        return beep;
    }

    public BooleanProperty rec() {
        return rec;
    }

    public BooleanProperty trunk() {
        return trunk;
    }

    public BooleanProperty priority() {
        return priority;
    }

    public ObjectProperty<Delay> delay() {
        return delay;
    }

    public ObjectProperty<Modulation> modulation() {
        return modulation;
    }

    public ObjectProperty<Step> step() {
        return step;
    }

    /** True when the content differs from the last read, write or save. */
    public ReadOnlyBooleanProperty modified() {
        return modified.getReadOnlyProperty();
    }

    public ChannelData data() {
        return new ChannelData(frequency.get(), tone.get(), tag.get(), att.get(), lockout.get(), beep.get(), rec.get(),
                trunk.get(), priority.get(), delay.get(), modulation.get(), step.get());
    }

    public void set(ChannelData d) {
        frequency.set(d.frequency());
        tone.set(d.tone());
        tag.set(d.tag());
        att.set(d.att());
        lockout.set(d.lockout());
        beep.set(d.beep());
        rec.set(d.rec());
        trunk.set(d.trunk());
        priority.set(d.priority());
        delay.set(d.delay());
        modulation.set(d.modulation());
        step.set(d.step());
    }

    /** Takes the current content as the reference for {@link #modified()}. */
    public void markClean() {
        baseline = data();
        modified.set(false);
    }
}
