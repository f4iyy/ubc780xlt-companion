package io.github.f4iyy.ubc780xlt.model;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** One of the 10 scan banks (a group of 50 channels) with its name. */
public final class ScanBank {

    private final int number;
    private final StringProperty tag = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(false);
    private ScanBankData baseline = new ScanBankData("");

    public ScanBank(int number) {
        this.number = number;
        tag.addListener((obs, old, now) -> modified.set(!data().equals(baseline)));
    }

    public int number() {
        return number;
    }

    public StringProperty tag() {
        return tag;
    }

    public ReadOnlyBooleanProperty modified() {
        return modified.getReadOnlyProperty();
    }

    public ScanBankData data() {
        return new ScanBankData(tag.get());
    }

    public void set(ScanBankData d) {
        tag.set(d.tag());
    }

    public void markClean() {
        baseline = data();
        modified.set(false);
    }
}
