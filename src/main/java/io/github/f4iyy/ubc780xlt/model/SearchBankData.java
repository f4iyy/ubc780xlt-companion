package io.github.f4iyy.ubc780xlt.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable content of one search bank. */
public record SearchBankData(BigDecimal lower, BigDecimal upper, String tag, Modulation modulation, Step step) {

    public SearchBankData {
        lower = lower == null ? BigDecimal.ZERO.setScale(4) : lower.setScale(4, RoundingMode.HALF_UP);
        upper = upper == null ? BigDecimal.ZERO.setScale(4) : upper.setScale(4, RoundingMode.HALF_UP);
        tag = tag == null ? "" : tag;
        modulation = modulation == null ? Modulation.AUTO : modulation;
        step = step == null ? Step.AUTO : step;
    }

    public static SearchBankData empty() {
        return new SearchBankData(null, null, null, null, null);
    }

    public boolean isEmpty() {
        return lower.signum() == 0 && upper.signum() == 0;
    }
}
