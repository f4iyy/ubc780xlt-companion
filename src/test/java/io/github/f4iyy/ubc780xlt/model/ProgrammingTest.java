package io.github.f4iyy.ubc780xlt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgrammingTest {

    private static ChannelData freq(String mhz) {
        return ChannelData.empty().withFrequency(new BigDecimal(mhz));
    }

    @Test
    void hasTheScannerSizes() {
        Programming p = new Programming();
        assertEquals(500, p.channels().size());
        assertEquals(10, p.scanBanks().size());
        assertEquals(10, p.searchBanks().size());
        assertEquals(50, p.bankChannels(3).size());
        assertEquals(101, p.bankChannels(3).get(0).number());
        assertEquals(150, p.bankChannels(3).get(49).number());
        assertEquals(3, p.channel(150).bank());
        assertEquals(4, p.channel(151).bank());
        assertEquals(10, p.channel(500).bank());
    }

    @Test
    void startsCleanAndTracksModification() {
        Programming p = new Programming();
        assertFalse(p.isModified());

        p.channel(7).tag().set("HELLO");
        assertTrue(p.channel(7).modified().get());
        assertTrue(p.isModified());

        p.channel(7).tag().set("");
        assertFalse(p.channel(7).modified().get(), "back to the reference value");
        assertFalse(p.isModified());

        p.channel(7).set(freq("145.5"));
        p.scanBanks().get(0).tag().set("WORK");
        p.searchBanks().get(1).tag().set("UHF");
        assertTrue(p.scanBanks().get(0).modified().get());
        assertTrue(p.searchBanks().get(1).modified().get());
        p.markClean();
        assertFalse(p.isModified());
        assertFalse(p.channel(7).modified().get());
    }

    @Test
    void snapshotAndApplyCopyContent() {
        Programming p = new Programming();
        p.channel(1).set(freq("145.5"));
        List<ChannelData> snap = p.snapshot();
        assertEquals(500, snap.size());

        p.channel(1).set(freq("146.5"));
        p.apply(0, snap.subList(0, 1));
        assertEquals(new BigDecimal("145.5000"), p.channel(1).data().frequency());
    }

    @Test
    void clearAndReplaceWith() {
        Programming a = new Programming();
        a.channel(2).set(freq("430.1"));
        a.scanBanks().get(2).tag().set("MARINE");
        a.searchBanks().get(4).set(new SearchBankData(new BigDecimal("108"), new BigDecimal("136"), "AERO",
                Modulation.AM, Step.AUTO));

        Programming b = new Programming();
        b.replaceWith(a);
        assertEquals(a.channel(2).data(), b.channel(2).data());
        assertEquals("MARINE", b.scanBanks().get(2).data().tag());
        assertEquals("AERO", b.searchBanks().get(4).data().tag());
        assertFalse(b.isModified());

        b.clear();
        assertTrue(b.channel(2).data().isEmpty());
        assertEquals("", b.scanBanks().get(2).data().tag());
        assertTrue(b.searchBanks().get(4).data().isEmpty());
    }
}
