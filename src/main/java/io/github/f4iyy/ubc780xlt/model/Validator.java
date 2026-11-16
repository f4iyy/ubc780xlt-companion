package io.github.f4iyy.ubc780xlt.model;

import io.github.f4iyy.ubc780xlt.protocol.FrequencyRanges;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Checks the programming against what the scanner accepts. */
public final class Validator {

    /** A problem found in a cell. */
    public record Issue(String sheet, int row, String column, String message) {
        @Override
        public String toString() {
            return sheet + " " + row + " (" + column + "): " + message;
        }
    }

    private Validator() {
    }

    /** A frequency is valid when unused (0) or inside a receivable band. */
    public static boolean isValidFrequency(BigDecimal mhz) {
        return mhz == null || mhz.signum() == 0 || FrequencyRanges.isValid(mhz);
    }

    public static List<Issue> validate(Programming p) {
        List<Issue> issues = new ArrayList<>();
        for (ScanBank b : p.scanBanks()) {
            tag(issues, "SCAN", b.number(), b.tag().get());
        }
        for (SearchBank b : p.searchBanks()) {
            SearchBankData d = b.data();
            tag(issues, "SEARCH", b.number(), d.tag());
            frequency(issues, "SEARCH", b.number(), "Lower Limit Freq", d.lower());
            frequency(issues, "SEARCH", b.number(), "Upper Limit Freq", d.upper());
            if (d.lower().signum() > 0 && d.upper().signum() > 0 && d.lower().compareTo(d.upper()) > 0) {
                issues.add(new Issue("SEARCH", b.number(), "Upper Limit Freq", "Upper limit is below the lower limit"));
            }
        }
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            int priorities = 0;
            for (Channel c : p.bankChannels(bank)) {
                ChannelData d = c.data();
                frequency(issues, "CHANNELS", c.number(), "Frequency", d.frequency());
                tag(issues, "CHANNELS", c.number(), d.tag());
                if (d.priority() && ++priorities > 1) {
                    issues.add(new Issue("CHANNELS", c.number(), "Pri", "Only one priority channel per bank"));
                }
            }
        }
        return issues;
    }

    private static void frequency(List<Issue> issues, String sheet, int row, String column, BigDecimal f) {
        if (!isValidFrequency(f)) {
            issues.add(new Issue(sheet, row, column, "Frequency " + f.toPlainString() + " MHz is outside the receivable bands"));
        }
    }

    private static void tag(List<Issue> issues, String sheet, int row, String tag) {
        if (!Tags.isValid(tag)) {
            issues.add(new Issue(sheet, row, "Text Tag", "Text tag must be at most 16 printable ASCII characters"));
        }
    }
}
