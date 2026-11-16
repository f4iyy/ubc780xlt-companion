package io.github.f4iyy.ubc780xlt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.Validator.Issue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    private static BigDecimal mhz(String s) {
        return new BigDecimal(s);
    }

    @Test
    void anEmptyProgrammingIsValid() {
        assertTrue(Validator.validate(new Programming()).isEmpty());
    }

    @Test
    void frequencyIsValidWhenUnusedOrInsideABand() {
        assertTrue(Validator.isValidFrequency(null));
        assertTrue(Validator.isValidFrequency(BigDecimal.ZERO));
        assertTrue(Validator.isValidFrequency(mhz("145.5")));
        assertFalse(Validator.isValidFrequency(mhz("700")));
    }

    @Test
    void reportsAChannelOutsideTheBands() {
        Programming p = new Programming();
        p.channel(12).set(ChannelData.empty().withFrequency(mhz("700")));
        List<Issue> issues = Validator.validate(p);
        assertEquals(1, issues.size());
        assertEquals("CHANNELS", issues.get(0).sheet());
        assertEquals(12, issues.get(0).row());
        assertEquals("Frequency", issues.get(0).column());
        assertTrue(issues.get(0).toString().startsWith("CHANNELS 12 (Frequency)"));
    }

    @Test
    void reportsBadTags() {
        Programming p = new Programming();
        p.channel(1).set(ChannelData.empty().withTag("this tag is far too long"));
        p.scanBanks().get(0).tag().set("café");
        p.searchBanks().get(0).tag().set("this tag is far too long");
        assertEquals(3, Validator.validate(p).size());
    }

    @Test
    void reportsASecondPriorityChannelInTheSameBank() {
        Programming p = new Programming();
        p.channel(1).set(ChannelData.empty().withPriority(true));
        p.channel(2).set(ChannelData.empty().withPriority(true));
        p.channel(51).set(ChannelData.empty().withPriority(true));
        List<Issue> issues = Validator.validate(p);
        assertEquals(1, issues.size());
        assertEquals(2, issues.get(0).row());
        assertEquals("Pri", issues.get(0).column());
    }

    @Test
    void checksSearchBankLimits() {
        Programming p = new Programming();
        p.searchBanks().get(0).set(new SearchBankData(mhz("146"), mhz("144"), "VHF", Modulation.AUTO, Step.AUTO));
        p.searchBanks().get(1).set(new SearchBankData(mhz("700"), mhz("710"), "BAD", Modulation.AUTO, Step.AUTO));
        List<Issue> issues = Validator.validate(p);
        assertEquals(3, issues.size());
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("below the lower")));
        assertEquals(2, issues.stream().filter(i -> i.row() == 2).count());
    }
}
