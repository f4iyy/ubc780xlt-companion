package io.github.f4iyy.ubc780xlt.ui.viewmodel;

import io.github.f4iyy.ubc780xlt.model.Channel;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.Tags;
import io.github.f4iyy.ubc780xlt.model.Validator;
import io.github.f4iyy.ubc780xlt.model.edit.ProgrammingEditor;
import io.github.f4iyy.ubc780xlt.model.edit.ProgrammingEditor.SortKey;
import io.github.f4iyy.ubc780xlt.model.edit.UndoManager;
import io.github.f4iyy.ubc780xlt.model.xlsx.XlsxStore;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The database tab: the scanner programming being edited, its file, undo/redo, and the operations behind the
 * buttons. Every change goes through the {@link ProgrammingEditor} so that it can be undone.
 */
public final class DatabaseViewModel {

    private static final Logger LOG = LogManager.getLogger(DatabaseViewModel.class);

    private final Programming programming = new Programming();
    private final UndoManager undo = new UndoManager();
    private final ProgrammingEditor editor = new ProgrammingEditor(programming, undo);
    private final Messages messages;

    private final ObjectProperty<Path> file = new SimpleObjectProperty<>();
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private final StringProperty message = new SimpleStringProperty("");

    public DatabaseViewModel(Messages messages) {
        this.messages = messages;
    }

    public Programming programming() {
        return programming;
    }

    public UndoManager history() {
        return undo;
    }

    public ProgrammingEditor editor() {
        return editor;
    }

    /** The file the programming was last opened from or saved to; {@code null} for a new one. */
    public ObjectProperty<Path> file() {
        return file;
    }

    /** True when there are changes since the last open or save. */
    public BooleanProperty modified() {
        return modified;
    }

    public StringProperty message() {
        return message;
    }

    // ---- files ------------------------------------------------------------------------------

    public void newProgramming() {
        programming.clear();
        undo.clear();
        file.set(null);
        refresh();
        message.set(messages.get("db.new"));
    }

    /** @return true if the file was loaded */
    public boolean open(Path path) {
        try {
            Programming loaded = XlsxStore.read(path);
            programming.replaceWith(loaded);
            undo.clear();
            file.set(path);
            refresh();
            List<Validator.Issue> issues = Validator.validate(programming);
            message.set(issues.isEmpty()
                    ? messages.get("db.opened", path.getFileName())
                    : messages.get("db.openedWithIssues", path.getFileName(), issues.size()));
            return true;
        } catch (IOException e) {
            LOG.warn("Cannot open {}: {}", path, e.getMessage());
            message.set(messages.get("db.error", e.getMessage()));
            return false;
        }
    }

    /** Saves to the current file. @return false if there is none or the save failed */
    public boolean save() {
        Path target = file.get();
        return target != null && saveAs(target);
    }

    public boolean saveAs(Path path) {
        try {
            XlsxStore.write(programming, path);
            programming.markClean();
            file.set(path);
            refresh();
            message.set(messages.get("db.saved", path.getFileName()));
            return true;
        } catch (IOException e) {
            LOG.warn("Cannot save {}: {}", path, e.getMessage());
            message.set(messages.get("db.error", e.getMessage()));
            return false;
        }
    }

    public List<Validator.Issue> issues() {
        return Validator.validate(programming);
    }

    /** Replaces the database by what was read from the scanner. It has no file yet. */
    public void adoptDownloaded(Programming read) {
        programming.replaceWith(read);
        undo.clear();
        file.set(null);
        refresh();
    }

    /** After an upload the scanner holds the database: rows are no longer "modified" since the last write. */
    public void markTransferred() {
        programming.markClean();
        refresh();
    }

    // ---- edits ------------------------------------------------------------------------------

    public void editChannel(int number, UnaryOperator<ChannelData> change) {
        editor.editChannel(number, change);
        refresh();
    }

    /** Sets a frequency typed in a cell; refuses values outside the receivable bands. @return true if applied */
    public boolean setFrequency(int number, BigDecimal mhz) {
        if (!Validator.isValidFrequency(mhz)) {
            message.set(messages.get("db.freqRange", mhz.stripTrailingZeros().toPlainString()));
            return false;
        }
        editChannel(number, d -> d.withFrequency(mhz));
        return true;
    }

    /** Sets a tag typed in a cell; refuses text the scanner cannot show. @return true if applied */
    public boolean setChannelTag(int number, String tag) {
        if (!Tags.isValid(tag)) {
            message.set(messages.get("db.tagInvalid"));
            return false;
        }
        editChannel(number, d -> d.withTag(tag));
        return true;
    }

    public void editScanBank(int bank, UnaryOperator<io.github.f4iyy.ubc780xlt.model.ScanBankData> change) {
        editor.editScanBank(bank, change);
        refresh();
    }

    public void editSearchBank(int bank, UnaryOperator<io.github.f4iyy.ubc780xlt.model.SearchBankData> change) {
        editor.editSearchBank(bank, change);
        refresh();
    }

    public void undo() {
        undo.undo();
        refresh();
    }

    public void redo() {
        undo.redo();
        refresh();
    }

    // ---- clipboard --------------------------------------------------------------------------

    /** The channels as tab-separated lines, one per row, in channel order. */
    public String copyRows(Collection<Integer> numbers) {
        return numbers.stream().sorted().map(n -> programming.channel(n).data().toTsv())
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /** Pastes lines made by {@link #copyRows} starting at channel {@code first}. @return false if the text is not channels */
    public boolean pasteRows(int first, String text) {
        List<ChannelData> rows = new ArrayList<>();
        try {
            for (String line : text.split("\\R")) {
                if (!line.isBlank()) {
                    rows.add(ChannelData.fromTsv(line));
                }
            }
        } catch (IllegalArgumentException e) {
            message.set(messages.get("db.pasteError", e.getMessage()));
            return false;
        }
        if (rows.isEmpty()) {
            return false;
        }
        editor.pasteChannels(first, rows);
        refresh();
        return true;
    }

    // ---- row operations and tools -----------------------------------------------------------

    public void deleteRows(Collection<Integer> numbers) {
        editor.clearChannels(numbers);
        refresh();
    }

    public void insertRow(int number) {
        try {
            editor.insertChannel(number);
        } catch (IllegalStateException e) {
            message.set(messages.get("db.bankFull"));
        }
        refresh();
    }

    public void moveRows(List<Integer> numbers, int destination) {
        try {
            editor.moveChannels(numbers, destination);
        } catch (IllegalArgumentException e) {
            message.set(messages.get("db.destination", destination));
        }
        refresh();
    }

    public void copyRowsTo(List<Integer> numbers, int destination) {
        try {
            editor.copyChannels(numbers, destination);
        } catch (IllegalArgumentException e) {
            message.set(messages.get("db.destination", destination));
        }
        refresh();
    }

    public void sortBank(int bank, SortKey key) {
        editor.sortBank(bank, key);
        refresh();
    }

    public int findReplace(String find, String replace, boolean matchCase) {
        int n = editor.findReplace(find, replace, matchCase);
        message.set(messages.get("db.replaced", n));
        refresh();
        return n;
    }

    /** Groups of channels with the same frequency. */
    public List<List<Integer>> findDuplicates() {
        List<List<Integer>> groups = editor.findDuplicates();
        message.set(groups.isEmpty() ? messages.get("db.noDuplicates") : messages.get("db.duplicates", groups.size()));
        return groups;
    }

    public int updateTextTags() {
        int n = editor.normalizeTags();
        message.set(messages.get("db.tagsUpdated", n));
        refresh();
        return n;
    }

    /** Channels of a bank, for the table of that bank. */
    public List<Channel> bankChannels(int bank) {
        return programming.bankChannels(bank);
    }

    private void refresh() {
        modified.set(programming.isModified());
    }
}
