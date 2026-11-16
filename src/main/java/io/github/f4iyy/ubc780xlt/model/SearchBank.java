package io.github.f4iyy.ubc780xlt.model;

import java.math.BigDecimal;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;

/** One of the 10 search banks: a frequency range to search, with a name. */
public final class SearchBank {

    private final int number;
    private final ObjectProperty<BigDecimal> lower = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> upper = new SimpleObjectProperty<>();
    private final StringProperty tag = new SimpleStringProperty();
    private final ObjectProperty<Modulation> modulation = new SimpleObjectProperty<>();
    private final ObjectProperty<Step> step = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(false);
    private SearchBankData baseline = SearchBankData.empty();

    public SearchBank(int number) {
        this.number = number;
        set(SearchBankData.empty());
        for (ObservableValue<?> p : new ObservableValue<?>[] {lower, upper, tag, modulation, step}) {
            p.addListener((obs, old, now) -> modified.set(!data().equals(baseline)));
        }
    }

    public int number() {
        return number;
    }

    public ObjectProperty<BigDecimal> lower() {
        return lower;
    }

    public ObjectProperty<BigDecimal> upper() {
        return upper;
    }

    public StringProperty tag() {
        return tag;
    }

    public ObjectProperty<Modulation> modulation() {
        return modulation;
    }

    public ObjectProperty<Step> step() {
        return step;
    }

    public ReadOnlyBooleanProperty modified() {
        return modified.getReadOnlyProperty();
    }

    public SearchBankData data() {
        return new SearchBankData(lower.get(), upper.get(), tag.get(), modulation.get(), step.get());
    }

    public void set(SearchBankData d) {
        lower.set(d.lower());
        upper.set(d.upper());
        tag.set(d.tag());
        modulation.set(d.modulation());
        step.set(d.step());
    }

    public void markClean() {
        baseline = data();
        modified.set(false);
    }
}
