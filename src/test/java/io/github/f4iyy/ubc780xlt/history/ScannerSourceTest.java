package io.github.f4iyy.ubc780xlt.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.history.ReceptionSource.Snapshot;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdParser;
import io.github.f4iyy.ubc780xlt.protocol.lcd.LcdState;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScannerSourceTest {

    private FakeScanner scanner;
    private ConnectionManager manager;
    private ScannerSource source;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        manager.connect("P", 0);
        source = new ScannerSource(manager);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void followsTheSquelch() throws IOException {
        assertFalse(source.squelchOpen());
        scanner.setSquelchOpen(true);
        assertTrue(source.squelchOpen());
        scanner.setSquelchOpen(false);
        assertFalse(source.squelchOpen());
    }

    @Test
    void readsTheSignalLevel() throws IOException {
        scanner.setSignal(142, 4303500);
        assertEquals(142, source.signal());
    }

    @Test
    void aSnapshotCombinesSignalFrequencyAndDisplay() throws IOException {
        scanner.setSignal(120, 4303500);
        Snapshot s = source.snapshot();

        assertEquals(120, s.signal());
        assertEquals("430.3500", s.frequency());
        assertEquals(183, s.channel());
        assertEquals(4, s.bank());
        assertEquals("", s.tag(), "'> SCANNING' is a prompt, not a tag");
        assertEquals("FM", s.modulation());
        assertEquals("", s.tone());
    }

    @Test
    void unreadableSignalAnswersGiveZero() throws IOException {
        ConnectionManager other = new ConnectionManager(
                new FakeLinkFactory().addPort("Q", FakeScanner.bc780xlt()), new ConnectionSettings(50, 0));
        other.connect("Q", 0);
        scanner.garbleNext(1);
        assertEquals(0, source.signal());
        other.close();
    }

    // ---- reading the display ----

    private static LcdState lcd(String... lines) {
        List<String> all = new ArrayList<>(List.of(lines));
        return LcdParser.parse(all);
    }

    @Test
    void channelAndBank() {
        assertEquals(188, ScannerSource.channelOf(lcd("CHN [188]")));
        assertEquals(12, ScannerSource.channelOf(lcd("CHN [ 12]")));
        assertEquals(0, ScannerSource.channelOf(lcd("CHN [bn6]")));
        assertEquals(0, ScannerSource.channelOf(lcd("CHN [501]")));
        assertEquals(0, ScannerSource.channelOf(lcd("CHN [000]")));
        assertEquals(1, ScannerSource.bankOf(lcd("CHN [50]"), 50));
        assertEquals(2, ScannerSource.bankOf(lcd("CHN [51]"), 51));
        assertEquals(10, ScannerSource.bankOf(lcd("CHN [500]"), 500));
        assertEquals(3, ScannerSource.bankOf(lcd("BNK --*-------"), 0), "searching: the flashing bank");
        assertEquals(0, ScannerSource.bankOf(lcd("BNK ----------"), 0));
    }

    @Test
    void tagIsTheFirstLineOfTheDisplay() {
        // Seen on a real scanner during a reception: line 1 = channel tag, line 2 = scan bank name.
        assertEquals("LFMN ATIS FR", ScannerSource.tagOf(lcd("LINE1 [LFMN ATIS FR    ][                ]",
                "LINE2 [AERO            ][                ]")));
        // Scanning: nothing on line 1, a prompt on line 2.
        assertEquals("", ScannerSource.tagOf(lcd("LINE1 [                ][                ]",
                "LINE2 [> SCANNING      ][                ]")));
        // A prompt on line 1 is not a tag either.
        assertEquals("", ScannerSource.tagOf(lcd("LINE1 [> SEARCH        ][                ]",
                "LINE2 [> SCANNING      ][                ]")));
    }

    @Test
    void modulationAndTone() {
        assertEquals("AM", ScannerSource.modulationOf(lcd("AM +")));
        assertEquals("FM", ScannerSource.modulationOf(lcd("FM +")));
        assertEquals("NFM", ScannerSource.modulationOf(lcd("FM +", "N +")));
        assertEquals("WFM", ScannerSource.modulationOf(lcd("WFM +")));
        assertEquals("", ScannerSource.modulationOf(lcd("P -")));

        assertEquals("123.0", ScannerSource.toneOf(lcd("CTCSS +", "CTC [ 123.0]")));
        assertEquals("023", ScannerSource.toneOf(lcd("DCS +", "CTC [ 023]")));
        assertEquals("", ScannerSource.toneOf(lcd("CTC [ 123.0]")), "no icon: it is not a tone");
    }
}
