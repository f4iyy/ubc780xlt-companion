package io.github.f4iyy.ubc780xlt.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.Response;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.Backlight;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.CtcssFunction;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.MuteMode;
import io.github.f4iyy.ubc780xlt.settings.SettingsClient.Outcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsClientTest {

    private FakeScanner scanner;
    private ConnectionManager manager;
    private SettingsClient client;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        manager.connect("P", 0);
        client = new SettingsClient(manager);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void readsTheDefaultsOfTheSimulatedScanner() throws IOException {
        SettingsClient.ReadResult r = client.read();
        ScannerSettings s = r.settings();

        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
        assertEquals(Backlight.NORMAL, s.backlight());
        assertEquals(false, s.beep());
        assertEquals(false, s.priority());
        assertEquals(true, s.dataSkip());
        assertEquals(MuteMode.AUTO, s.mute());
        assertEquals(false, s.editLock());
        assertEquals(false, s.screenMask());
        assertEquals(CtcssFunction.OFF, s.ctcss());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), s.scanBanks());
        assertEquals(Set.of(1), s.searchBanks());
        assertTrue(s.skipList().isEmpty());
    }

    @Test
    void writtenSettingsAreReadBack() throws IOException {
        assertEquals(Outcome.OK, client.setBacklight(Backlight.DIM));
        assertEquals(Outcome.OK, client.setBeep(true));
        assertEquals(Outcome.OK, client.setPriority(true));
        assertEquals(Outcome.OK, client.setDataSkip(false));
        assertEquals(Outcome.OK, client.setMute(MuteMode.CLOSED));
        assertEquals(Outcome.OK, client.setEditLock(true));
        assertEquals(Outcome.OK, client.setScreenMask(true));
        assertEquals(Outcome.OK, client.setCtcss(CtcssFunction.SEARCH));
        assertEquals(Outcome.OK, client.setScanBanks(Set.of(4, 1, 7)));
        assertEquals(Outcome.OK, client.setSearchBanks(Set.of(2, 3)));

        ScannerSettings s = client.read().settings();
        assertEquals(Backlight.DIM, s.backlight());
        assertTrue(s.beep() && s.priority() && s.editLock() && s.screenMask());
        assertEquals(false, s.dataSkip());
        assertEquals(MuteMode.CLOSED, s.mute());
        assertEquals(CtcssFunction.SEARCH, s.ctcss());
        assertEquals(Set.of(1, 4, 7), s.scanBanks());
        assertEquals(Set.of(2, 3), s.searchBanks());
        assertTrue(scanner.received().contains("SB ADG"), "banks are sent as sorted letters");
    }

    @Test
    void theSkipListIsReadAndExtended() throws IOException {
        assertEquals(Outcome.OK, client.addSkipFrequency(new BigDecimal("466.45")));
        assertEquals(Outcome.OK, client.addSkipFrequency(new BigDecimal("466.45")), "already there is fine");
        assertEquals(Outcome.OK, client.addSkipFrequency(new BigDecimal("145.5")));

        assertEquals(List.of(new BigDecimal("466.4500"), new BigDecimal("145.5000")), client.read().settings().skipList());
        assertEquals(List.of(4664500L, 1455000L), scanner.skipList());
    }

    @Test
    void anUnparsableAnswerGivesADefaultAndAWarning() throws IOException {
        CommandSender broken = command -> new Response(Response.Kind.DATA, "???");
        SettingsClient.ReadResult r = new SettingsClient(broken).read();
        assertEquals(Backlight.NORMAL, r.settings().backlight());
        assertEquals(MuteMode.AUTO, r.settings().mute());
        assertTrue(r.settings().scanBanks().isEmpty());
        assertTrue(r.warnings().size() >= 10, r.warnings().toString());
    }

    @Test
    void outcomesFollowTheScannersAnswer() throws IOException {
        assertEquals(Outcome.NOT_NOW, new SettingsClient(c -> new Response(Response.Kind.NG, "NG")).setBeep(true));
        assertEquals(Outcome.ERROR, new SettingsClient(c -> new Response(Response.Kind.ERR, "ERR")).setBeep(true));
        assertEquals(Outcome.ERROR,
                new SettingsClient(c -> new Response(Response.Kind.DATA, "?")).addSkipFrequency(BigDecimal.ONE));
        assertEquals(Outcome.NOT_NOW,
                new SettingsClient(c -> new Response(Response.Kind.NG, "NG")).addSkipFrequency(BigDecimal.ONE));
    }

    @Test
    void enumsFindTheirCodes() {
        assertEquals(Backlight.OFF, Backlight.of('F'));
        assertNull(Backlight.of('X'));
        assertEquals(MuteMode.OPEN, MuteMode.of('N'));
        assertNull(MuteMode.of('X'));
        assertEquals(CtcssFunction.SEARCH, CtcssFunction.of('S'));
        assertNull(CtcssFunction.of('X'));
    }
}
