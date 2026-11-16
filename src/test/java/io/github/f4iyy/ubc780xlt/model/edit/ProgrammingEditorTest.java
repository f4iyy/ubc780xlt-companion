package io.github.f4iyy.ubc780xlt.model.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.edit.ProgrammingEditor.SortKey;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgrammingEditorTest {

    private Programming p;
    private UndoManager undo;
    private ProgrammingEditor editor;

    @BeforeEach
    void setUp() {
        p = new Programming();
        undo = new UndoManager();
        editor = new ProgrammingEditor(p, undo);
    }

    private void set(int number, String mhz, String tag) {
        p.channel(number).set(ChannelData.empty().withFrequency(new BigDecimal(mhz)).withTag(tag));
        p.markClean();
    }

    private String freq(int number) {
        return p.channel(number).data().frequency().toPlainString();
    }

    // ---- cell edits and undo ----

    @Test
    void editingAChannelIsUndoableAndRedoable() {
        editor.editChannel(5, d -> d.withTag("RELAY"));
        assertEquals("RELAY", p.channel(5).data().tag());
        assertTrue(undo.canUndo().get());
        assertFalse(undo.canRedo().get());

        undo.undo();
        assertEquals("", p.channel(5).data().tag());
        assertTrue(undo.canRedo().get());

        undo.redo();
        assertEquals("RELAY", p.channel(5).data().tag());
    }

    @Test
    void anEditThatChangesNothingIsNotRecorded() {
        editor.editChannel(5, d -> d);
        assertFalse(undo.canUndo().get());
        editor.editScanBank(1, d -> d);
        editor.editSearchBank(1, d -> d);
        assertFalse(undo.canUndo().get());
    }

    @Test
    void aNewEditClearsTheRedoHistory() {
        editor.editChannel(1, d -> d.withTag("A"));
        undo.undo();
        editor.editChannel(1, d -> d.withTag("B"));
        assertFalse(undo.canRedo().get());
        assertEquals("Edit channel 1", undo.undoName());
    }

    @Test
    void undoAndRedoOnAnEmptyHistoryDoNothing() {
        undo.undo();
        undo.redo();
        assertEquals("", undo.undoName());
    }

    @Test
    void historyIsLimitedAndCanBeCleared() {
        for (int i = 0; i < 250; i++) {
            int n = i;
            editor.editChannel(1, d -> d.withTag("T" + n));
        }
        int undone = 0;
        while (undo.canUndo().get()) {
            undo.undo();
            undone++;
        }
        assertEquals(200, undone);
        editor.editChannel(1, d -> d.withTag("X"));
        undo.clear();
        assertFalse(undo.canUndo().get());
        assertFalse(undo.canRedo().get());
    }

    @Test
    void bankEditsAreUndoable() {
        editor.editScanBank(2, d -> new ScanBankData("PMR"));
        editor.editSearchBank(3, d -> new SearchBankData(new BigDecimal("430"), new BigDecimal("435"), "UHF",
                Modulation.FM, Step.K25));
        assertEquals("PMR", p.scanBanks().get(1).data().tag());
        assertEquals("UHF", p.searchBanks().get(2).data().tag());
        assertEquals("Edit search bank 3", undo.undoName());

        undo.undo();
        undo.undo();
        assertEquals("", p.scanBanks().get(1).data().tag());
        assertTrue(p.searchBanks().get(2).data().isEmpty());
        undo.redo();
        undo.redo();
        assertEquals("UHF", p.searchBanks().get(2).data().tag());
    }

    @Test
    void oneBankHasOnlyOnePriorityChannel() {
        assertTrue(p.channel(1).data().priority(), "factory default: first channel of the bank");
        editor.editChannel(3, d -> d.withPriority(true));
        assertFalse(p.channel(1).data().priority());
        assertTrue(p.channel(3).data().priority());

        editor.editChannel(7, d -> d.withPriority(true));
        assertFalse(p.channel(3).data().priority());
        assertTrue(p.channel(7).data().priority());

        // Another bank is independent.
        editor.editChannel(60, d -> d.withPriority(true));
        assertTrue(p.channel(7).data().priority());
        assertFalse(p.channel(51).data().priority());

        undo.undo();
        undo.undo();
        undo.undo();
        assertTrue(p.channel(1).data().priority());
        assertFalse(p.channel(3).data().priority());
        assertTrue(p.channel(51).data().priority());
    }

    // ---- row operations ----

    @Test
    void clearingRowsEmptiesThem() {
        set(1, "145.5", "A");
        set(2, "146.5", "B");
        editor.clearChannels(List.of(1, 2));
        assertTrue(p.channel(1).data().isEmpty());
        assertTrue(p.channel(2).data().isEmpty());
        undo.undo();
        assertEquals("145.5000", freq(1));
        assertEquals("146.5000", freq(2));
    }

    @Test
    void movingRowsEmptiesTheSourceAndOverwritesTheDestination() {
        set(1, "145.1", "A");
        set(2, "145.2", "B");
        set(10, "999", "OLD");
        editor.moveChannels(List.of(2, 1), 10);

        assertTrue(p.channel(1).data().isEmpty());
        assertTrue(p.channel(2).data().isEmpty());
        assertEquals("A", p.channel(10).data().tag());
        assertEquals("B", p.channel(11).data().tag());

        undo.undo();
        assertEquals("A", p.channel(1).data().tag());
        assertEquals("OLD", p.channel(10).data().tag());
        assertTrue(p.channel(11).data().isEmpty());
    }

    @Test
    void copyingKeepsTheSource() {
        set(1, "145.1", "A");
        editor.copyChannels(List.of(1), 120);
        assertEquals("A", p.channel(1).data().tag());
        assertEquals("A", p.channel(120).data().tag());
    }

    @Test
    void moveAndCopyCheckTheDestination() {
        set(1, "145.1", "A");
        set(2, "145.2", "B");
        assertThrows(IllegalArgumentException.class, () -> editor.moveChannels(List.of(1, 2), 500));
        assertThrows(IllegalArgumentException.class, () -> editor.copyChannels(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> editor.copyChannels(List.of(1), 501));
        editor.moveChannels(List.of(), 5); // nothing selected: nothing happens
        assertFalse(undo.canUndo().get());
        assertEquals("A", p.channel(1).data().tag());
    }

    @Test
    void pasteWritesFromTheFirstRowAndStopsAtChannel500() {
        List<ChannelData> clip = List.of(
                ChannelData.empty().withFrequency(new BigDecimal("1")).withTag("X"),
                ChannelData.empty().withFrequency(new BigDecimal("2")).withTag("Y"),
                ChannelData.empty().withFrequency(new BigDecimal("3")).withTag("Z"));
        editor.pasteChannels(499, clip);
        assertEquals("X", p.channel(499).data().tag());
        assertEquals("Y", p.channel(500).data().tag());
    }

    @Test
    void insertPushesTheFollowingChannelsOfTheBankDown() {
        set(3, "145.3", "C");
        set(4, "145.4", "D");
        editor.insertChannel(3);
        assertTrue(p.channel(3).data().isEmpty());
        assertEquals("C", p.channel(4).data().tag());
        assertEquals("D", p.channel(5).data().tag());
        undo.undo();
        assertEquals("C", p.channel(3).data().tag());
        assertEquals("D", p.channel(4).data().tag());
    }

    @Test
    void insertIsRefusedWhenTheBankIsFull() {
        set(50, "145.0", "LAST");
        assertThrows(IllegalStateException.class, () -> editor.insertChannel(1));
        assertEquals("LAST", p.channel(50).data().tag());
        assertFalse(undo.canUndo().get());
    }

    @Test
    void sortingByFrequencyPutsUnusedChannelsLast() {
        set(1, "446.0", "C");
        set(3, "145.0", "A");
        set(5, "430.0", "B");
        editor.sortBank(1, SortKey.FREQUENCY);
        assertEquals("A", p.channel(1).data().tag());
        assertEquals("B", p.channel(2).data().tag());
        assertEquals("C", p.channel(3).data().tag());
        assertTrue(p.channel(4).data().isEmpty());
        assertTrue(p.channel(50).data().isEmpty());
        assertEquals("Sort bank 1", undo.undoName());
    }

    @Test
    void sortingByTag() {
        set(1, "446.0", "banana");
        set(2, "145.0", "Apple");
        set(3, "430.0", "cherry");
        editor.sortBank(1, SortKey.TAG);
        assertEquals("Apple", p.channel(1).data().tag());
        assertEquals("banana", p.channel(2).data().tag());
        assertEquals("cherry", p.channel(3).data().tag());
    }

    // ---- tools ----

    @Test
    void findReplaceChangesTagsEverywhereAsOneUndoStep() {
        set(1, "145.0", "Relais VHF");
        set(60, "430.0", "relais UHF");
        p.scanBanks().get(0).tag().set("Relais");
        p.searchBanks().get(0).tag().set("Zone relais");
        p.markClean();

        int changed = editor.findReplace("relais", "Rel", false);
        assertEquals(4, changed);
        assertEquals("Rel VHF", p.channel(1).data().tag());
        assertEquals("Rel UHF", p.channel(60).data().tag());
        assertEquals("Rel", p.scanBanks().get(0).data().tag());
        assertEquals("Zone Rel", p.searchBanks().get(0).data().tag());

        undo.undo();
        assertEquals("Relais VHF", p.channel(1).data().tag());
        assertEquals("Relais", p.scanBanks().get(0).data().tag());
        assertEquals("Zone relais", p.searchBanks().get(0).data().tag());
        assertFalse(undo.canUndo().get(), "a single step");
    }

    @Test
    void findReplaceCanMatchCase() {
        set(1, "145.0", "Relais relais");
        assertEquals(1, editor.findReplace("relais", "X", true));
        assertEquals("Relais X", p.channel(1).data().tag());
        assertEquals(0, editor.findReplace("", "X", true));
        assertEquals(0, editor.findReplace(null, "X", true));
        assertEquals(0, editor.findReplace("nothing here", "X", true));
    }

    @Test
    void duplicatesAreChannelsWithTheSameFrequency() {
        set(1, "145.5", "A");
        set(80, "145.5", "B");
        set(200, "145.5", "C");
        set(2, "430.0", "D");
        set(3, "430.0", "E");
        set(4, "100.0", "F");

        List<List<Integer>> dups = editor.findDuplicates();
        assertEquals(2, dups.size());
        assertTrue(dups.contains(List.of(1, 80, 200)));
        assertTrue(dups.contains(List.of(2, 3)));
    }

    @Test
    void normalizingTagsCleansThemAllInOneStep() {
        set(1, "145.0", "  too   many   spaces  ");
        p.channel(2).tag().set("0123456789ABCDEFGHIJ");
        p.scanBanks().get(0).tag().set("  PMR ");
        p.searchBanks().get(0).tag().set("OK");
        p.markClean();

        assertEquals(3, editor.normalizeTags());
        assertEquals("too many spaces", p.channel(1).data().tag());
        assertEquals("0123456789ABCDEF", p.channel(2).data().tag());
        assertEquals("PMR", p.scanBanks().get(0).data().tag());
        assertEquals(0, editor.normalizeTags());

        undo.undo();
        assertEquals("  too   many   spaces  ", p.channel(1).data().tag());
    }
}
