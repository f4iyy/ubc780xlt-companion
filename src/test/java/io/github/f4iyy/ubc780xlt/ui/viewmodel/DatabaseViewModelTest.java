package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.edit.ProgrammingEditor.SortKey;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseViewModelTest {

    @TempDir
    Path dir;

    private Messages messages;
    private DatabaseViewModel vm;

    @BeforeEach
    void setUp() {
        messages = Messages.of("en");
        vm = new DatabaseViewModel(messages);
    }

    private static BigDecimal mhz(String s) {
        return new BigDecimal(s);
    }

    @Test
    void startsCleanWithoutAFile() {
        assertFalse(vm.modified().get());
        assertNull(vm.file().get());
        assertEquals(10, vm.bankChannels(4).size() / 5);
    }

    @Test
    void editsMarkTheProgrammingModifiedAndUndoClearsIt() {
        assertTrue(vm.setFrequency(1, mhz("145.5")));
        assertTrue(vm.modified().get());
        vm.undo();
        assertFalse(vm.modified().get());
        vm.redo();
        assertTrue(vm.modified().get());
    }

    @Test
    void aFrequencyOutsideTheBandsIsRefusedWithAMessage() {
        assertFalse(vm.setFrequency(1, mhz("700")));
        assertEquals(messages.get("db.freqRange", "700"), vm.message().get());
        assertFalse(vm.modified().get());
        assertTrue(vm.setFrequency(1, BigDecimal.ZERO), "zero clears the channel");
    }

    @Test
    void aBadTagIsRefusedWithAMessage() {
        assertFalse(vm.setChannelTag(1, "this tag is much too long"));
        assertEquals(messages.get("db.tagInvalid"), vm.message().get());
        assertTrue(vm.setChannelTag(1, "OK"));
        assertEquals("OK", vm.programming().channel(1).data().tag());
    }

    @Test
    void saveAndOpenRoundTrip() throws IOException {
        vm.setFrequency(3, mhz("430.35"));
        vm.setChannelTag(3, "RELAY");
        vm.editScanBank(1, d -> new ScanBankData("WORK"));
        vm.editSearchBank(2, d -> new SearchBankData(mhz("430"), mhz("440"), "UHF", d.modulation(), d.step()));
        Path file = dir.resolve("a.xlsx");

        assertTrue(vm.saveAs(file));
        assertFalse(vm.modified().get());
        assertEquals(file, vm.file().get());
        assertEquals(messages.get("db.saved", file.getFileName()), vm.message().get());
        assertTrue(Files.exists(file));

        DatabaseViewModel other = new DatabaseViewModel(messages);
        assertTrue(other.open(file));
        assertEquals("RELAY", other.programming().channel(3).data().tag());
        assertEquals("WORK", other.programming().scanBanks().get(0).data().tag());
        assertEquals("UHF", other.programming().searchBanks().get(1).data().tag());
        assertFalse(other.modified().get());
        assertEquals(messages.get("db.opened", file.getFileName()), other.message().get());
    }

    @Test
    void saveNeedsAFile() {
        assertFalse(vm.save());
        Path file = dir.resolve("b.xlsx");
        vm.saveAs(file);
        vm.setChannelTag(1, "X");
        assertTrue(vm.save());
        assertFalse(vm.modified().get());
    }

    @Test
    void savingToAnUnwritablePlaceReportsTheError() {
        assertFalse(vm.saveAs(dir.resolve("missing-folder/x.xlsx")));
        assertTrue(vm.message().get().startsWith("Error:"));
    }

    @Test
    void openingABadFileReportsTheErrorAndKeepsTheData() throws IOException {
        vm.setChannelTag(1, "KEEP");
        Path junk = dir.resolve("junk.xlsx");
        Files.writeString(junk, "not a workbook");
        assertFalse(vm.open(junk));
        assertTrue(vm.message().get().startsWith("Error:"));
        assertEquals("KEEP", vm.programming().channel(1).data().tag());
    }

    @Test
    void openingAFileWithValuesTheScannerRefusesIsReported() throws IOException {
        // Write directly through the model so that an out-of-range frequency reaches the file.
        vm.programming().channel(2).set(ChannelData.empty().withFrequency(mhz("700")));
        Path file = dir.resolve("bad.xlsx");
        vm.saveAs(file);

        DatabaseViewModel other = new DatabaseViewModel(messages);
        assertTrue(other.open(file));
        assertEquals(messages.get("db.openedWithIssues", file.getFileName(), 1), other.message().get());
        assertEquals(1, other.issues().size());
    }

    @Test
    void newProgrammingClearsEverything() {
        vm.setChannelTag(1, "X");
        vm.saveAs(dir.resolve("c.xlsx"));
        vm.setChannelTag(2, "Y");
        vm.newProgramming();
        assertTrue(vm.programming().channel(1).data().isEmpty());
        assertNull(vm.file().get());
        assertFalse(vm.modified().get());
        assertFalse(vm.history().canUndo().get());
    }

    @Test
    void clipboardRoundTrip() {
        vm.setFrequency(1, mhz("145.5"));
        vm.setChannelTag(1, "A");
        vm.setFrequency(2, mhz("146.5"));
        String text = vm.copyRows(List.of(2, 1));
        assertEquals(2, text.split("\n").length);

        assertTrue(vm.pasteRows(10, text));
        assertEquals("A", vm.programming().channel(10).data().tag());
        assertEquals(mhz("146.5000"), vm.programming().channel(11).data().frequency());
    }

    @Test
    void pastingSomethingElseIsRefused() {
        assertFalse(vm.pasteRows(1, "hello world"));
        assertTrue(vm.message().get().startsWith("Cannot paste"));
        assertFalse(vm.pasteRows(1, "  \n "));
        assertFalse(vm.modified().get());
    }

    @Test
    void rowOperations() {
        vm.setFrequency(1, mhz("145.1"));
        vm.setFrequency(2, mhz("145.2"));

        vm.copyRowsTo(List.of(1), 20);
        assertEquals(mhz("145.1000"), vm.programming().channel(20).data().frequency());

        vm.moveRows(List.of(2), 30);
        assertTrue(vm.programming().channel(2).data().isEmpty());
        assertEquals(mhz("145.2000"), vm.programming().channel(30).data().frequency());

        vm.insertRow(1);
        assertTrue(vm.programming().channel(1).data().isEmpty());
        assertEquals(mhz("145.1000"), vm.programming().channel(2).data().frequency());

        vm.deleteRows(List.of(2));
        assertTrue(vm.programming().channel(2).data().isEmpty());
    }

    @Test
    void badDestinationsAndFullBanksAreReported() {
        vm.setFrequency(1, mhz("145.1"));
        vm.moveRows(List.of(1), 600);
        assertEquals(messages.get("db.destination", 600), vm.message().get());
        vm.copyRowsTo(List.of(1), 0);
        assertEquals(messages.get("db.destination", 0), vm.message().get());

        vm.setFrequency(50, mhz("145.9"));
        vm.insertRow(1);
        assertEquals(messages.get("db.bankFull"), vm.message().get());
    }

    @Test
    void toolsReportWhatTheyDid() {
        vm.setFrequency(1, mhz("145.5"));
        vm.setChannelTag(1, "Relais");
        vm.setFrequency(2, mhz("145.5"));
        vm.setFrequency(3, mhz("100"));

        assertEquals(1, vm.findReplace("Relais", "R", true));
        assertEquals(messages.get("db.replaced", 1), vm.message().get());

        assertEquals(1, vm.findDuplicates().size());
        assertEquals(messages.get("db.duplicates", 1), vm.message().get());
        vm.setFrequency(2, BigDecimal.ZERO);
        assertTrue(vm.findDuplicates().isEmpty());
        assertEquals(messages.get("db.noDuplicates"), vm.message().get());

        vm.programming().channel(3).tag().set("  a   b ");
        assertEquals(1, vm.updateTextTags());
        assertEquals(messages.get("db.tagsUpdated", 1), vm.message().get());

        vm.sortBank(1, SortKey.FREQUENCY);
        assertEquals(mhz("100.0000"), vm.programming().channel(1).data().frequency());
    }
}
