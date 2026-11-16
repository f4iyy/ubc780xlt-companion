package io.github.f4iyy.ubc780xlt.model.edit;

import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Tags;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Every change to the programming goes through here, so that each one is undoable. Channel operations
 * work on a copy of the 500 channels and are recorded as one edit covering what changed.
 */
public final class ProgrammingEditor {

    /** What to sort a bank by. */
    public enum SortKey {
        FREQUENCY,
        TAG
    }

    private final Programming programming;
    private final UndoManager undo;
    private List<Edit> group;

    public ProgrammingEditor(Programming programming, UndoManager undo) {
        this.programming = programming;
        this.undo = undo;
    }

    public Programming programming() {
        return programming;
    }

    // ---- single cells and rows -------------------------------------------------------------

    /**
     * Changes one channel. Setting the priority of a channel clears it on the other channels of the bank
     * (the scanner has one priority channel per bank).
     */
    public void editChannel(int number, UnaryOperator<ChannelData> change) {
        channelOp("Edit channel " + number, list -> {
            ChannelData before = list.get(number - 1);
            ChannelData after = change.apply(before);
            list.set(number - 1, after);
            if (after.priority() && !before.priority()) {
                int first = (programming.channel(number).bank() - 1) * Programming.CHANNELS_PER_BANK;
                for (int i = first; i < first + Programming.CHANNELS_PER_BANK; i++) {
                    if (i != number - 1 && list.get(i).priority()) {
                        list.set(i, list.get(i).withPriority(false));
                    }
                }
            }
        });
    }

    public void editScanBank(int bank, UnaryOperator<ScanBankData> change) {
        ScanBankData before = programming.scanBanks().get(bank - 1).data();
        ScanBankData after = change.apply(before);
        if (!before.equals(after)) {
            apply(new ScanBankEdit(programming, bank - 1, before, after));
        }
    }

    public void editSearchBank(int bank, UnaryOperator<SearchBankData> change) {
        SearchBankData before = programming.searchBanks().get(bank - 1).data();
        SearchBankData after = change.apply(before);
        if (!before.equals(after)) {
            apply(new SearchBankEdit(programming, bank - 1, before, after));
        }
    }

    // ---- row operations ---------------------------------------------------------------------

    /** Empties the given channels. */
    public void clearChannels(Collection<Integer> numbers) {
        channelOp("Delete rows", list -> numbers.forEach(n -> list.set(n - 1, ChannelData.empty())));
    }

    /**
     * Moves the given channels (in order) so that the first one lands on {@code destination}; the places they
     * leave are emptied and what was at the destination is overwritten.
     *
     * @throws IllegalArgumentException if the block would go past channel 500
     */
    public void moveChannels(List<Integer> numbers, int destination) {
        List<Integer> sorted = sorted(numbers);
        checkDestination(sorted, destination);
        channelOp("Move rows", list -> {
            List<ChannelData> moved = sorted.stream().map(n -> list.get(n - 1)).toList();
            sorted.forEach(n -> list.set(n - 1, ChannelData.empty()));
            place(list, moved, destination);
        });
    }

    /** Copies the given channels to {@code destination}, overwriting what is there. */
    public void copyChannels(List<Integer> numbers, int destination) {
        List<Integer> sorted = sorted(numbers);
        checkDestination(sorted, destination);
        channelOp("Copy rows", list -> {
            List<ChannelData> copied = sorted.stream().map(n -> list.get(n - 1)).toList();
            place(list, copied, destination);
        });
    }

    /** Writes channels (e.g. from the clipboard) starting at {@code first}; extra rows past 500 are ignored. */
    public void pasteChannels(int first, List<ChannelData> data) {
        channelOp("Paste rows", list -> {
            for (int i = 0; i < data.size() && first - 1 + i < Programming.CHANNELS; i++) {
                list.set(first - 1 + i, data.get(i));
            }
        });
    }

    /**
     * Inserts an empty channel at {@code number}, pushing the following channels of the bank down.
     *
     * @throws IllegalStateException if the last channel of the bank is in use (it would be lost)
     */
    public void insertChannel(int number) {
        int bankEnd = programming.channel(number).bank() * Programming.CHANNELS_PER_BANK;
        channelOp("Insert row", list -> {
            if (!list.get(bankEnd - 1).isEmpty()) {
                throw new IllegalStateException("The bank is full");
            }
            for (int i = bankEnd - 1; i > number - 1; i--) {
                list.set(i, list.get(i - 1));
            }
            list.set(number - 1, ChannelData.empty());
        });
    }

    /** Sorts the channels of a bank; unused channels go last. */
    public void sortBank(int bank, SortKey key) {
        int first = (bank - 1) * Programming.CHANNELS_PER_BANK;
        Comparator<ChannelData> byFrequency = Comparator.comparing(ChannelData::frequency);
        Comparator<ChannelData> byTag = Comparator.comparing(c -> c.tag().toLowerCase());
        Comparator<ChannelData> order = key == SortKey.FREQUENCY ? byFrequency : byTag.thenComparing(byFrequency);
        channelOp("Sort bank " + bank, list -> {
            List<ChannelData> part = new ArrayList<>(list.subList(first, first + Programming.CHANNELS_PER_BANK));
            List<ChannelData> used = new ArrayList<>(part.stream().filter(c -> !c.isEmpty()).toList());
            used.sort(order);
            List<ChannelData> sorted = new ArrayList<>(used);
            part.stream().filter(ChannelData::isEmpty).forEach(sorted::add);
            for (int i = 0; i < sorted.size(); i++) {
                list.set(first + i, sorted.get(i));
            }
        });
    }

    // ---- tools ----------------------------------------------------------------------------

    /**
     * Replaces text in every alpha tag (channels, scan banks, search banks) as one undoable step.
     *
     * @return the number of tags changed
     */
    public int findReplace(String find, String replace, boolean matchCase) {
        if (find == null || find.isEmpty()) {
            return 0;
        }
        int[] count = {0};
        UnaryOperator<String> sub = tag -> {
            String result = matchCase
                    ? tag.replace(find, replace)
                    : tag.replaceAll("(?i)" + java.util.regex.Pattern.quote(find),
                            java.util.regex.Matcher.quoteReplacement(replace));
            if (!result.equals(tag)) {
                count[0]++;
            }
            return Tags.clean(result);
        };
        grouped("Find & replace", () -> {
            channelOp("Find & replace", list -> list.replaceAll(c -> c.withTag(sub.apply(c.tag()))));
            for (int i = 1; i <= Programming.BANKS; i++) {
                editScanBank(i, d -> new ScanBankData(sub.apply(d.tag())));
                editSearchBank(i, d -> new SearchBankData(d.lower(), d.upper(), sub.apply(d.tag()), d.modulation(),
                        d.step()));
            }
        });
        return count[0];
    }

    /** Groups of channel numbers that have the same frequency (unused channels are ignored). */
    public List<List<Integer>> findDuplicates() {
        Map<BigDecimal, List<Integer>> byFrequency = new LinkedHashMap<>();
        for (var c : programming.channels()) {
            ChannelData d = c.data();
            if (!d.isEmpty()) {
                byFrequency.computeIfAbsent(d.frequency(), k -> new ArrayList<>()).add(c.number());
            }
        }
        return byFrequency.values().stream().filter(g -> g.size() > 1).toList();
    }

    /**
     * Cleans every alpha tag: trims, collapses blanks, replaces characters the scanner cannot show and cuts to 16.
     *
     * @return the number of tags changed
     */
    public int normalizeTags() {
        int[] count = {0};
        UnaryOperator<String> clean = tag -> {
            String result = Tags.clean(tag);
            if (!result.equals(tag)) {
                count[0]++;
            }
            return result;
        };
        grouped("Update text tags", () -> {
            channelOp("Update text tags", list -> list.replaceAll(c -> c.withTag(clean.apply(c.tag()))));
            for (int i = 1; i <= Programming.BANKS; i++) {
                editScanBank(i, d -> new ScanBankData(clean.apply(d.tag())));
                editSearchBank(i, d -> new SearchBankData(d.lower(), d.upper(), clean.apply(d.tag()), d.modulation(),
                        d.step()));
            }
        });
        return count[0];
    }

    // ---- history ----------------------------------------------------------------------------

    public UndoManager history() {
        return undo;
    }

    // ---- internals --------------------------------------------------------------------------

    private static List<Integer> sorted(Collection<Integer> numbers) {
        return new ArrayList<>(new TreeSet<>(numbers));
    }

    private static void checkDestination(List<Integer> numbers, int destination) {
        if (numbers.isEmpty()) {
            return;
        }
        if (destination < 1 || destination + numbers.size() - 1 > Programming.CHANNELS) {
            throw new IllegalArgumentException("Destination out of range: " + destination);
        }
    }

    private static void place(List<ChannelData> list, List<ChannelData> data, int destination) {
        for (int i = 0; i < data.size(); i++) {
            list.set(destination - 1 + i, data.get(i));
        }
    }

    /** Runs the change on a copy of the channels and records what actually changed as one edit. */
    private void channelOp(String name, Consumer<List<ChannelData>> change) {
        List<ChannelData> before = programming.snapshot();
        List<ChannelData> after = new ArrayList<>(before);
        change.accept(after);
        int first = 0;
        while (first < after.size() && before.get(first).equals(after.get(first))) {
            first++;
        }
        if (first == after.size()) {
            return;
        }
        int last = after.size() - 1;
        while (before.get(last).equals(after.get(last))) {
            last--;
        }
        apply(new ChannelsEdit(name, programming, first, List.copyOf(before.subList(first, last + 1)),
                List.copyOf(after.subList(first, last + 1))));
    }

    private void apply(Edit edit) {
        edit.redo();
        if (group != null) {
            group.add(edit);
        } else {
            undo.record(edit);
        }
    }

    /** Runs {@code body}; every edit it makes becomes one undo step. */
    private void grouped(String name, Runnable body) {
        group = new ArrayList<>();
        try {
            body.run();
        } finally {
            List<Edit> edits = group;
            group = null;
            if (!edits.isEmpty()) {
                undo.record(new CompositeEdit(name, edits));
            }
        }
    }

    // ---- edit types -------------------------------------------------------------------------

    private record ChannelsEdit(String name, Programming p, int first, List<ChannelData> before, List<ChannelData> after)
            implements Edit {
        @Override
        public void undo() {
            p.apply(first, before);
        }

        @Override
        public void redo() {
            p.apply(first, after);
        }
    }

    private record ScanBankEdit(Programming p, int index, ScanBankData before, ScanBankData after) implements Edit {
        @Override
        public String name() {
            return "Edit scan bank " + (index + 1);
        }

        @Override
        public void undo() {
            p.scanBanks().get(index).set(before);
        }

        @Override
        public void redo() {
            p.scanBanks().get(index).set(after);
        }
    }

    private record SearchBankEdit(Programming p, int index, SearchBankData before, SearchBankData after)
            implements Edit {
        @Override
        public String name() {
            return "Edit search bank " + (index + 1);
        }

        @Override
        public void undo() {
            p.searchBanks().get(index).set(before);
        }

        @Override
        public void redo() {
            p.searchBanks().get(index).set(after);
        }
    }

    private record CompositeEdit(String name, List<Edit> edits) implements Edit {
        @Override
        public void undo() {
            for (int i = edits.size() - 1; i >= 0; i--) {
                edits.get(i).undo();
            }
        }

        @Override
        public void redo() {
            edits.forEach(Edit::redo);
        }
    }
}
