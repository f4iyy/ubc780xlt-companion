package io.github.f4iyy.ubc780xlt.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TuneServiceTest {

    private FakeScanner scanner;
    private ConnectionManager manager;
    private TuneService tune;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        manager.connect("P", 0);
        tune = new TuneService(manager);
    }

    /** Commands sent after the connection set-up (SI, IDF, QUF, RIF). */
    private List<String> sent() {
        return scanner.received().subList(4, scanner.received().size());
    }

    @Test
    void goesToManualModeThenSetsStepFrequencyAndModulation() throws IOException {
        tune.tune(new BigDecimal("145.475"), Step.K12_5, Modulation.NFM);

        assertEquals(List.of("MD", "KEY01", "ST 12.5K", "RF01454750", "RM NFM"), sent());
        assertEquals("145.4750", scanner.frequencyText());
        assertEquals(8, scanner.mode());
    }

    @Test
    void staysInManualModeWhenAlreadyThere() throws IOException {
        manager.pressKey(ScannerKey.MAN, false);
        int before = scanner.received().size();
        tune.tune(new BigDecimal("430.35"), Step.AUTO, Modulation.AUTO);

        List<String> after = scanner.received().subList(before, scanner.received().size());
        assertEquals(List.of("MD", "ST AUTO", "RF04303500", "RM AUTO"), after);
    }

    @Test
    void refusesAFrequencyOutsideTheBands() {
        assertThrows(IllegalArgumentException.class, () -> tune.tune(new BigDecimal("700"), Step.AUTO, Modulation.AUTO));
        assertEquals(4, scanner.received().size(), "nothing may be sent");
    }

    @Test
    void reportsAnErrorAnswer() {
        // MD is answered ERR (not a manual mode), then KEY01 is refused too.
        scanner.garbleNext(2);
        IOException e = assertThrows(IOException.class,
                () -> tune.tune(new BigDecimal("145.475"), Step.AUTO, Modulation.AUTO));
        assertTrue(e.getMessage().contains("refused"), e.getMessage());
    }

    @Test
    void stepLabelsMatchTheScannerSyntax() {
        assertEquals("AUTO", Step.AUTO.toString());
        assertEquals("7.5K", Step.K7_5.toString());
        assertEquals("100K", Step.K100.command());
    }
}
