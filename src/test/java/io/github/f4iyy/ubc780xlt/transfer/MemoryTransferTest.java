package io.github.f4iyy.ubc780xlt.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tone;
import io.github.f4iyy.ubc780xlt.protocol.CommandSender;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionManager;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionSettings;
import io.github.f4iyy.ubc780xlt.protocol.Response;
import io.github.f4iyy.ubc780xlt.protocol.ScannerTimeoutException;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeLinkFactory;
import io.github.f4iyy.ubc780xlt.protocol.sim.FakeScanner;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryTransferTest {

    private FakeScanner scanner;
    private ConnectionManager manager;

    @BeforeEach
    void setUp() throws IOException {
        scanner = FakeScanner.bc780xlt();
        manager = new ConnectionManager(new FakeLinkFactory().addPort("P", scanner), new ConnectionSettings(50, 0));
        manager.connect("P", 0);
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    private static BigDecimal mhz(String s) {
        return new BigDecimal(s);
    }

    /** A programming that uses every kind of value. */
    private static Programming sample() {
        Programming p = new Programming();
        p.scanBanks().get(0).set(new ScanBankData("WORK"));
        p.scanBanks().get(9).set(new ScanBankData("AERO"));
        p.searchBanks().get(0).set(new SearchBankData(mhz("144"), mhz("146"), "RA VHF", Modulation.AUTO, Step.AUTO));
        p.searchBanks().get(4).set(new SearchBankData(mhz("108"), mhz("136"), "AERO", Modulation.AUTO, Step.AUTO));
        p.channel(1).set(new ChannelData(mhz("145.475"), new Tone(18, true), "CALL", true, false, true, false,
                true, true, Delay.S2, Modulation.NFM, Step.K12_5));
        p.channel(2).set(new ChannelData(mhz("430.35"), new Tone(39, false), "UHF RELAY", false, true, false, true,
                false, false, Delay.NONE, Modulation.FM, Step.K25));
        p.channel(51).set(p.channel(51).data().withPriority(false)); // bank 2's priority moves to channel 75
        p.channel(75).set(new ChannelData(mhz("121.5"), Tone.OFF, "AERO GUARD", false, false, false, false, false,
                true, Delay.M10, Modulation.AM, Step.AUTO));
        p.channel(500).set(new ChannelData(mhz("1296.5"), new Tone(142, false), "LAST", false, true, false, true,
                false, false, Delay.INFINITE, Modulation.WFM, Step.K100));
        p.markClean();
        return p;
    }

    private static void assertSame(Programming expected, Programming actual) {
        for (int n = 1; n <= Programming.CHANNELS; n++) {
            assertEquals(expected.channel(n).data(), actual.channel(n).data(), "channel " + n);
        }
        for (int b = 0; b < Programming.BANKS; b++) {
            assertEquals(expected.scanBanks().get(b).data(), actual.scanBanks().get(b).data(), "scan bank " + (b + 1));
            assertEquals(expected.searchBanks().get(b).data(), actual.searchBanks().get(b).data(),
                    "search bank " + (b + 1));
        }
    }

    // ---- reading ----

    @Test
    void readsTheWholeMemory() throws Exception {
        Programming original = sample();
        scanner.loadMemory(original);

        MemoryReader reader = new MemoryReader(manager, Progress.NONE, () -> false);
        Programming read = reader.read();

        assertSame(original, read);
        assertTrue(reader.warnings().isEmpty(), reader.warnings().toString());
        assertFalse(read.isModified());
        assertEquals(1, scanner.mode(), "visiting channels leaves the scanner in manual mode");
    }

    @Test
    void unusedChannelsAreReadWithOneCommand() throws Exception {
        scanner.loadMemory(sample());
        int before = scanner.received().size();
        new MemoryReader(manager, Progress.NONE, () -> false).read();
        List<String> sent = scanner.received().subList(before, scanner.received().size());

        assertEquals(1, sent.stream().filter(c -> c.equals("PM003")).count());
        assertEquals(0, sent.stream().filter(c -> c.equals("MA003")).count(), "empty channel is not visited");
        assertEquals(1, sent.stream().filter(c -> c.equals("MA001")).count());
        assertEquals(1, sent.stream().filter(c -> c.equals("TA C 001")).count());
    }

    @Test
    void readingReportsProgress() throws Exception {
        List<Integer> done = new ArrayList<>();
        List<String> tasks = new ArrayList<>();
        new MemoryReader(manager, (task, d, total) -> {
            done.add(d);
            tasks.add(task);
            assertEquals(530, total);
        }, () -> false).read();

        assertEquals(0, done.get(0));
        assertEquals(530, done.get(done.size() - 1));
        assertEquals("Scan bank 1", tasks.get(0));
        assertTrue(tasks.contains("Channel 500"));
        assertEquals("Done", tasks.get(tasks.size() - 1));
    }

    @Test
    void unexpectedAnswersBecomeWarningsAndTheTransferGoesOn() throws Exception {
        scanner.loadMemory(sample());
        scanner.garbleNext(4); // the first four commands (scan bank name, ...) answer ERR

        MemoryReader reader = new MemoryReader(manager, Progress.NONE, () -> false);
        Programming read = reader.read();

        assertFalse(reader.warnings().isEmpty());
        assertEquals("CALL", read.channel(1).data().tag(), "the rest was still read");
    }

    @Test
    void cancellingStopsTheRead() {
        int[] calls = {0};
        MemoryReader reader = new MemoryReader(manager, Progress.NONE, () -> ++calls[0] > 25);
        assertThrows(TransferCancelledException.class, reader::read);
    }

    @Test
    void aBrokenLinkAbortsTheRead() {
        MemoryReader reader = new MemoryReader(failingAfter(40), Progress.NONE, () -> false);
        assertThrows(IOException.class, reader::read);
    }

    // ---- writing ----

    @Test
    void writesTheWholeMemory() throws Exception {
        Programming original = sample();

        MemoryWriter writer = new MemoryWriter(manager, Progress.NONE, () -> false);
        writer.write(original);

        assertSame(original, scanner.memory());
        assertTrue(writer.warnings().isEmpty(), writer.warnings().toString());
    }

    @Test
    void channelsAreWrittenInTheOrderTheScannerNeeds() throws Exception {
        Programming p = new Programming();
        p.channel(7).set(new ChannelData(mhz("145.475"), new Tone(1, false), "X", true, false, true, true, true,
                false, Delay.S4, Modulation.FM, Step.K10));
        p.markClean();
        int before = scanner.received().size();

        new MemoryWriter(manager, Progress.NONE, () -> false).write(p);

        List<String> sent = scanner.received().subList(before, scanner.received().size()).stream()
                .filter(c -> c.contains("007") || c.startsWith("ST ") || c.startsWith("RM ") || c.startsWith("DL")
                        || c.startsWith("AT") || c.startsWith("AR") || c.startsWith("LO") || c.startsWith("CS"))
                .filter(c -> !c.startsWith("PM00") || c.startsWith("PM007"))
                .toList();
        // The part for channel 7: MA, ST, RM, PM (trunk flag), then what PM resets, then tone and tag.
        int ma = sent.indexOf("MA007");
        assertEquals("ST 10K", sent.get(ma + 1));
        assertEquals("PM007T01454750", sent.get(ma + 2));
        assertEquals("RM FM", sent.get(ma + 3)); // PM resets the modulation to the band plan's
        assertEquals("DLN +4", sent.get(ma + 4));
        assertEquals("ATN", sent.get(ma + 5));
        assertEquals("ARN", sent.get(ma + 6));
        assertEquals("LOF", sent.get(ma + 7));
        assertTrue(sent.contains("BAN C 007"));
        assertTrue(sent.contains("CS001"));
    }

    @Test
    void emptyTagsAreWrittenAsBlanksBecauseABareCommandIsARead() throws Exception {
        scanner.loadMemory(sample());
        Programming empty = new Programming();
        new MemoryWriter(manager, Progress.NONE, () -> false).write(empty);

        assertEquals("", scanner.memory().channel(1).data().tag());
        assertEquals("", scanner.memory().scanBanks().get(0).data().tag());
        assertTrue(scanner.received().contains("TA C 001 " + " ".repeat(16)));
    }

    @Test
    void writingReportsProgressAndCanBeCancelled() throws Exception {
        List<Integer> done = new ArrayList<>();
        new MemoryWriter(manager, (t, d, total) -> done.add(d), () -> false).write(new Programming());
        assertEquals(530, done.get(done.size() - 1));

        int[] calls = {0};
        MemoryWriter writer = new MemoryWriter(manager, Progress.NONE, () -> ++calls[0] > 30);
        assertThrows(TransferCancelledException.class, () -> writer.write(sample()));
    }

    @Test
    void refusedCommandsAreWarningsNotFailures() throws Exception {
        scanner.garbleNext(3);
        MemoryWriter writer = new MemoryWriter(manager, Progress.NONE, () -> false);
        writer.write(sample());
        assertEquals(3, writer.warnings().size());
        assertTrue(writer.warnings().get(0).startsWith("Scan bank 1: TA B A"), writer.warnings().get(0));
    }

    @Test
    void aBrokenLinkAbortsTheWrite() {
        MemoryWriter writer = new MemoryWriter(failingAfter(40), Progress.NONE, () -> false);
        assertThrows(IOException.class, () -> writer.write(sample()));
    }

    @Test
    void unusedSearchBanksAreNotWrittenBecauseTheScannerRefusesZeroLimits() throws Exception {
        int before = scanner.received().size();
        MemoryWriter writer = new MemoryWriter(manager, Progress.NONE, () -> false);
        writer.write(sample());

        List<String> sent = scanner.received().subList(before, scanner.received().size());
        assertTrue(sent.contains("LL01440000 A"));
        assertTrue(sent.contains("LU01360000 E"));
        assertTrue(sent.stream().noneMatch(c -> c.startsWith("LL00000000") || c.startsWith("LU00000000")), sent.toString());
        assertTrue(writer.warnings().isEmpty(), writer.warnings().toString());
    }

    @Test
    void aRefusedToneOffIsNotAWarningButARefusedToneIs() throws Exception {
        CommandSender refusing = command -> command.startsWith("CS") ? new Response(Response.Kind.NG, "NG")
                : manager.send(command);
        Programming p = new Programming();
        p.channel(124).set(new ChannelData(mhz("121.5"), Tone.OFF, "AERO", false, false, false, false, false,
                false, Delay.NONE, Modulation.AM, Step.AUTO));
        p.markClean();

        MemoryWriter writer = new MemoryWriter(refusing, Progress.NONE, () -> false);
        writer.write(p);
        assertTrue(writer.warnings().isEmpty(), writer.warnings().toString());

        p.channel(124).set(p.channel(124).data().withTone(new Tone(18, false)));
        MemoryWriter writer2 = new MemoryWriter(refusing, Progress.NONE, () -> false);
        writer2.write(p);
        assertEquals(1, writer2.warnings().size());
        assertTrue(writer2.warnings().get(0).contains("CS018 answered NG"), writer2.warnings().get(0));
    }

    @Test
    void aCommandRepeatedByTheScannerIsAConfirmation() throws Exception {
        CommandSender echoing = command -> command.startsWith("LL") || command.startsWith("LU")
                || command.startsWith("PC") ? new Response(Response.Kind.DATA, command) : manager.send(command);
        MemoryWriter writer = new MemoryWriter(echoing, Progress.NONE, () -> false);
        writer.write(sample());
        assertTrue(writer.warnings().isEmpty(), writer.warnings().toString());

        CommandSender other = command -> command.startsWith("LL") ? new Response(Response.Kind.DATA, "LL99999999 A")
                : manager.send(command);
        MemoryWriter writer2 = new MemoryWriter(other, Progress.NONE, () -> false);
        writer2.write(sample());
        assertFalse(writer2.warnings().isEmpty());
    }

    // ---- both ----

    @Test
    void whatIsWrittenCanBeReadBack() throws Exception {
        Programming original = sample();
        new MemoryWriter(manager, Progress.NONE, () -> false).write(original);
        Programming back = new MemoryReader(manager, Progress.NONE, () -> false).read();
        assertSame(original, back);
    }

    // ---- retries ----

    @Test
    void aSilentScannerIsAskedAgainOnce() throws Exception {
        boolean[] first = {true};
        CommandSender flaky = command -> {
            if (first[0]) {
                first[0] = false;
                throw new ScannerTimeoutException(command, 50);
            }
            return manager.send(command);
        };
        MemoryWriter writer = new MemoryWriter(flaky, Progress.NONE, () -> false);
        writer.write(new Programming());
        assertTrue(writer.warnings().isEmpty());
        assertEquals(1, scanner.received().stream().filter(c -> c.startsWith("TA B A")).count());
    }

    @Test
    void twoSilentAnswersInARowFail() {
        CommandSender mute = command -> {
            throw new ScannerTimeoutException(command, 50);
        };
        assertThrows(ScannerTimeoutException.class,
                () -> new MemoryWriter(mute, Progress.NONE, () -> false).write(new Programming()));
        assertThrows(ScannerTimeoutException.class,
                () -> new MemoryReader(mute, Progress.NONE, () -> false).read());
    }

    private static CommandSender failingAfter(int commands) {
        int[] n = {0};
        return command -> {
            if (++n[0] > commands) {
                throw new IOException("Cable removed");
            }
            return new Response(Response.Kind.OK, "OK");
        };
    }
}
