package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.model.Channel;
import io.github.f4iyy.ubc780xlt.model.ChannelData;
import io.github.f4iyy.ubc780xlt.model.Delay;
import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Programming;
import io.github.f4iyy.ubc780xlt.model.ScanBank;
import io.github.f4iyy.ubc780xlt.model.ScanBankData;
import io.github.f4iyy.ubc780xlt.model.SearchBank;
import io.github.f4iyy.ubc780xlt.model.SearchBankData;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.model.Tags;
import io.github.f4iyy.ubc780xlt.model.Tone;
import io.github.f4iyy.ubc780xlt.model.Validator;
import io.github.f4iyy.ubc780xlt.model.edit.ProgrammingEditor.SortKey;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.DatabaseViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.TransferViewModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.ButtonType;
import javafx.util.StringConverter;

/**
 * The database tab: editable tables for channels (one tab per bank), scan banks and search banks, and the
 * buttons of ProScan (download/upload, find &amp; replace, duplicates, tags, sort, move/copy/delete rows, undo).
 */
public final class DatabaseView extends BorderPane {

    private static final String STYLE_MODIFIED = "row-modified";

    private final DatabaseViewModel vm;
    private final TransferViewModel transfer;
    private final Messages messages;
    private final TabPane bankTabs = new TabPane();
    private final List<TableView<Channel>> bankTables = new ArrayList<>();

    public DatabaseView(DatabaseViewModel vm, TransferViewModel transfer, Messages messages) {
        this.vm = vm;
        this.transfer = transfer;
        this.messages = messages;

        Tab channels = new Tab(messages.get("tab.channels"), buildChannels());
        Tab banks = new Tab(messages.get("tab.banks"), buildBanks());
        channels.setClosable(false);
        banks.setClosable(false);
        TabPane tabs = new TabPane(channels, banks);
        setCenter(tabs);
        setRight(buildButtons());
        setPadding(new Insets(6, 14, 6, 14));
    }

    // ---- channels ---------------------------------------------------------------------------

    private TabPane buildChannels() {
        Programming p = vm.programming();
        for (int bank = 1; bank <= Programming.BANKS; bank++) {
            int n = bank;
            ScanBank scan = p.scanBanks().get(bank - 1);
            TableView<Channel> table = channelTable(bank);
            bankTables.add(table);
            Tab tab = new Tab();
            tab.setClosable(false);
            tab.setContent(table);
            tab.textProperty().bind(Bindings.createStringBinding(() -> {
                String tag = scan.tag().get();
                return messages.get("db.bank", n) + (tag.isEmpty() ? "" : " - " + tag);
            }, scan.tag()));
            bankTabs.getTabs().add(tab);
        }
        return bankTabs;
    }

    private TableView<Channel> channelTable(int bank) {
        TableView<Channel> t = new TableView<>(FXCollections.observableArrayList(vm.bankChannels(bank)));
        t.setEditable(true);
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        t.setRowFactory(tv -> new ChannelRow());

        TableColumn<Channel, Integer> number = new TableColumn<>("Channel");
        number.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().number()));
        number.setEditable(false);
        number.setPrefWidth(70);

        TableColumn<Channel, BigDecimal> frequency = new TableColumn<>("Frequency");
        frequency.setCellValueFactory(c -> c.getValue().frequency());
        frequency.setCellFactory(col -> new TextFieldTableCell<>(new FrequencyConverter()));
        frequency.setOnEditCommit(e -> {
            Channel ch = e.getRowValue();
            if (e.getNewValue() == null) {
                vm.message().set(messages.get("tune.invalid"));
                e.getTableView().refresh();
            } else if (!vm.setFrequency(ch.number(), e.getNewValue())) {
                e.getTableView().refresh();
            }
        });
        frequency.setPrefWidth(110);

        TableColumn<Channel, Tone> tone = new TableColumn<>("Tone");
        tone.setCellValueFactory(c -> c.getValue().tone());
        tone.setCellFactory(col -> new ComboBoxTableCell<>(new ToneConverter(), FXCollections.observableArrayList(allTones())));
        tone.setOnEditCommit(e -> vm.editChannel(e.getRowValue().number(), d -> d.withTone(e.getNewValue())));
        tone.setPrefWidth(110);

        TableColumn<Channel, String> tag = new TableColumn<>("Text Tag");
        tag.setCellValueFactory(c -> c.getValue().tag());
        tag.setCellFactory(TextFieldTableCell.forTableColumn());
        tag.setOnEditCommit(e -> {
            if (!vm.setChannelTag(e.getRowValue().number(), e.getNewValue())) {
                e.getTableView().refresh();
            }
        });
        tag.setPrefWidth(160);

        TableColumn<Channel, Delay> delay = new TableColumn<>("Delay");
        delay.setCellValueFactory(c -> c.getValue().delay());
        delay.setCellFactory(ComboBoxTableCell.forTableColumn(Delay.values()));
        delay.setOnEditCommit(e -> vm.editChannel(e.getRowValue().number(), d -> d.withDelay(e.getNewValue())));
        delay.setPrefWidth(80);

        TableColumn<Channel, Modulation> mod = new TableColumn<>("Mod");
        mod.setCellValueFactory(c -> c.getValue().modulation());
        mod.setCellFactory(ComboBoxTableCell.forTableColumn(Modulation.values()));
        mod.setOnEditCommit(e -> vm.editChannel(e.getRowValue().number(), d -> d.withModulation(e.getNewValue())));
        mod.setPrefWidth(80);

        TableColumn<Channel, Step> step = new TableColumn<>("Steps");
        step.setCellValueFactory(c -> c.getValue().step());
        step.setCellFactory(ComboBoxTableCell.forTableColumn(Step.values()));
        step.setOnEditCommit(e -> vm.editChannel(e.getRowValue().number(), d -> d.withStep(e.getNewValue())));
        step.setPrefWidth(80);

        t.getColumns().addAll(List.of(number, frequency, tone, tag,
                check("Att", Channel::att, ChannelData::withAtt),
                check("LO", Channel::lockout, ChannelData::withLockout),
                check("Beep", Channel::beep, ChannelData::withBeep),
                check("Rec", Channel::rec, ChannelData::withRec),
                check("Trunk", Channel::trunk, ChannelData::withTrunk),
                check("Pri", Channel::priority, ChannelData::withPriority),
                delay, mod, step));

        t.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
                copyToClipboard(t);
                e.consume();
            } else if (e.isShortcutDown() && e.getCode() == KeyCode.V) {
                pasteFromClipboard(t);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE && !t.getSelectionModel().isEmpty()) {
                vm.deleteRows(numbers(t));
                e.consume();
            }
        });
        MenuItem insert = new MenuItem(messages.get("db.insertRow"));
        insert.setOnAction(e -> insertRow(t));
        MenuItem copy = new MenuItem(messages.get("db.copyRows"));
        copy.setOnAction(e -> copyToClipboard(t));
        MenuItem delete = new MenuItem(messages.get("db.deleteRows"));
        delete.setOnAction(e -> vm.deleteRows(numbers(t)));
        t.setContextMenu(new ContextMenu(insert, copy, delete));
        return t;
    }

    /** A check box column: each click is an undoable edit. */
    private TableColumn<Channel, Boolean> check(String title, Function<Channel, javafx.beans.property.BooleanProperty> property,
            BiFunction<ChannelData, Boolean, ChannelData> change) {
        TableColumn<Channel, Boolean> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> property.apply(c.getValue()));
        col.setPrefWidth(52);
        col.setSortable(false);
        col.setCellFactory(c -> new TableCell<>() {
            private final CheckBox box = new CheckBox();

            {
                setAlignment(Pos.CENTER);
                box.setOnAction(e -> {
                    Channel ch = getTableRow() == null ? null : (Channel) getTableRow().getItem();
                    if (ch != null) {
                        vm.editChannel(ch.number(), d -> change.apply(d, box.isSelected()));
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    box.setSelected(item);
                    setGraphic(box);
                }
            }
        });
        return col;
    }

    private static List<Tone> allTones() {
        List<Tone> tones = new ArrayList<>();
        for (int code = 0; code <= io.github.f4iyy.ubc780xlt.model.ToneTable.MAX_CODE; code++) {
            tones.add(new Tone(code, false));
            if (code > 0) {
                tones.add(new Tone(code, true));
            }
        }
        return tones;
    }

    /** Highlights channels changed since the last read or save. */
    private static final class ChannelRow extends TableRow<Channel> {
        private final ChangeListener<Boolean> listener = (obs, old, now) -> style(now);
        private Channel watched;

        @Override
        protected void updateItem(Channel item, boolean empty) {
            super.updateItem(item, empty);
            if (watched != null) {
                watched.modified().removeListener(listener);
            }
            watched = empty ? null : item;
            if (watched != null) {
                watched.modified().addListener(listener);
            }
            style(watched != null && watched.modified().get());
        }

        private void style(boolean modified) {
            getStyleClass().remove(STYLE_MODIFIED);
            if (modified) {
                getStyleClass().add(STYLE_MODIFIED);
            }
        }
    }

    // ---- banks ------------------------------------------------------------------------------

    private HBox buildBanks() {
        Programming p = vm.programming();

        TableView<ScanBank> scan = new TableView<>(FXCollections.observableArrayList(p.scanBanks()));
        scan.setEditable(true);
        TableColumn<ScanBank, Integer> sn = new TableColumn<>("Bank");
        sn.setCellValueFactory(c -> Bindings.createObjectBinding(() -> c.getValue().number()));
        sn.setPrefWidth(60);
        TableColumn<ScanBank, String> st = new TableColumn<>("Text Tag");
        st.setCellValueFactory(c -> c.getValue().tag());
        st.setCellFactory(TextFieldTableCell.forTableColumn());
        st.setPrefWidth(180);
        st.setOnEditCommit(e -> {
            if (Tags.isValid(e.getNewValue())) {
                vm.editScanBank(e.getRowValue().number(), d -> new ScanBankData(e.getNewValue()));
            } else {
                vm.message().set(messages.get("db.tagInvalid"));
                e.getTableView().refresh();
            }
        });
        scan.getColumns().addAll(List.of(sn, st));
        scan.setPrefWidth(280);

        TableView<SearchBank> search = new TableView<>(FXCollections.observableArrayList(p.searchBanks()));
        search.setEditable(true);
        TableColumn<SearchBank, Integer> n = new TableColumn<>("Bank");
        n.setCellValueFactory(c -> Bindings.createObjectBinding(() -> c.getValue().number()));
        n.setPrefWidth(60);
        TableColumn<SearchBank, BigDecimal> lower = new TableColumn<>("Lower Limit Freq");
        lower.setCellValueFactory(c -> c.getValue().lower());
        lower.setCellFactory(col -> new TextFieldTableCell<>(new FrequencyConverter()));
        lower.setOnEditCommit(e -> limit(e, true));
        lower.setPrefWidth(130);
        TableColumn<SearchBank, BigDecimal> upper = new TableColumn<>("Upper Limit Freq");
        upper.setCellValueFactory(c -> c.getValue().upper());
        upper.setCellFactory(col -> new TextFieldTableCell<>(new FrequencyConverter()));
        upper.setOnEditCommit(e -> limit(e, false));
        upper.setPrefWidth(130);
        TableColumn<SearchBank, String> tag = new TableColumn<>("Text Tag");
        tag.setCellValueFactory(c -> c.getValue().tag());
        tag.setCellFactory(TextFieldTableCell.forTableColumn());
        tag.setOnEditCommit(e -> {
            if (Tags.isValid(e.getNewValue())) {
                vm.editSearchBank(e.getRowValue().number(), d -> new SearchBankData(d.lower(), d.upper(),
                        e.getNewValue(), d.modulation(), d.step()));
            } else {
                vm.message().set(messages.get("db.tagInvalid"));
                e.getTableView().refresh();
            }
        });
        tag.setPrefWidth(150);
        TableColumn<SearchBank, Modulation> mod = new TableColumn<>("Mod");
        mod.setCellValueFactory(c -> c.getValue().modulation());
        mod.setCellFactory(ComboBoxTableCell.forTableColumn(Modulation.values()));
        mod.setOnEditCommit(e -> vm.editSearchBank(e.getRowValue().number(), d -> new SearchBankData(d.lower(),
                d.upper(), d.tag(), e.getNewValue(), d.step())));
        mod.setPrefWidth(80);
        TableColumn<SearchBank, Step> step = new TableColumn<>("Steps");
        step.setCellValueFactory(c -> c.getValue().step());
        step.setCellFactory(ComboBoxTableCell.forTableColumn(Step.values()));
        step.setOnEditCommit(e -> vm.editSearchBank(e.getRowValue().number(), d -> new SearchBankData(d.lower(),
                d.upper(), d.tag(), d.modulation(), e.getNewValue())));
        step.setPrefWidth(80);
        search.getColumns().addAll(List.of(n, lower, upper, tag, mod, step));
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox left = new VBox(6, new Label(messages.get("db.scanBanks")), scan);
        VBox right = new VBox(6, new Label(messages.get("db.searchBanks")), search);
        VBox.setVgrow(scan, Priority.ALWAYS);
        VBox.setVgrow(search, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox box = new HBox(12, left, right);
        box.setPadding(new Insets(8, 0, 0, 0));
        return box;
    }

    private void limit(TableColumn.CellEditEvent<SearchBank, BigDecimal> e, boolean isLower) {
        BigDecimal value = e.getNewValue();
        if (value == null || !Validator.isValidFrequency(value)) {
            vm.message().set(messages.get("db.freqRange", value == null ? "?" : value.toPlainString()));
            e.getTableView().refresh();
            return;
        }
        vm.editSearchBank(e.getRowValue().number(), d -> isLower
                ? new SearchBankData(value, d.upper(), d.tag(), d.modulation(), d.step())
                : new SearchBankData(d.lower(), value, d.tag(), d.modulation(), d.step()));
    }

    // ---- buttons ----------------------------------------------------------------------------

    private VBox buildButtons() {
        Button download = button("db.download", () -> confirmThen("transfer.confirmDownload", transfer::download));
        Button upload = button("db.upload", () -> confirmThen("transfer.confirmUpload", transfer::upload));
        download.disableProperty().bind(transfer.available().not());
        upload.disableProperty().bind(transfer.available().not());
        download.setTooltip(new Tooltip(messages.get("transfer.tooltip")));
        upload.setTooltip(new Tooltip(messages.get("transfer.tooltip")));

        ProgressBar bar = new ProgressBar();
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(transfer.progress());
        Label taskLabel = new Label();
        taskLabel.getStyleClass().add("hint");
        taskLabel.textProperty().bind(transfer.task());
        taskLabel.setWrapText(true);
        Button cancel = button("transfer.cancel", transfer::cancel);
        VBox progressBox = new VBox(6, bar, taskLabel, cancel);
        progressBox.visibleProperty().bind(transfer.running());
        progressBox.managedProperty().bind(transfer.running());
        transfer.warnings().addListener((obs, old, now) -> {
            if (!now.isEmpty()) {
                showWarnings(now);
            }
        });

        Button undo = button("db.undo", vm::undo);
        undo.disableProperty().bind(vm.history().canUndo().not());
        Button redo = button("db.redo", vm::redo);
        redo.disableProperty().bind(vm.history().canRedo().not());

        VBox box = new VBox(6,
                section("db.database"), download, upload, progressBox,
                section("db.allChannels"),
                button("db.findReplace", this::findReplace),
                button("db.findDuplicates", this::findDuplicates),
                button("db.updateTags", vm::updateTextTags),
                button("db.sort", this::sort),
                button("db.insertRow", () -> insertRow(currentTable())),
                button("db.moveRows", () -> moveOrCopy(true)),
                button("db.copyRows", () -> moveOrCopy(false)),
                button("db.deleteRows", () -> vm.deleteRows(numbers(currentTable()))),
                undo, redo);
        box.setPadding(new Insets(8, 0, 0, 14));
        box.setMinWidth(190);
        return box;
    }

    private void confirmThen(String messageKey, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, messages.get(messageKey), ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(messages.get("transfer.title"));
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> action.run());
    }

    private void showWarnings(List<String> warnings) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(messages.get("transfer.warningsTitle"));
        alert.setHeaderText(messages.get("transfer.warnings", warnings.size()));
        TextArea area = new TextArea(String.join("\n", warnings));
        area.setEditable(false);
        area.setPrefRowCount(14);
        alert.getDialogPane().setContent(area);
        style(alert);
        alert.show();
    }

    private Label section(String key) {
        Label l = new Label(messages.get(key));
        l.getStyleClass().add("hint");
        return l;
    }

    private Button button(String key, Runnable action) {
        Button b = new Button(messages.get(key));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }

    private TableView<Channel> currentTable() {
        return bankTables.get(Math.max(0, bankTabs.getSelectionModel().getSelectedIndex()));
    }

    private int currentBank() {
        return Math.max(0, bankTabs.getSelectionModel().getSelectedIndex()) + 1;
    }

    private static List<Integer> numbers(TableView<Channel> t) {
        return t.getSelectionModel().getSelectedItems().stream().map(Channel::number).sorted().toList();
    }

    private void insertRow(TableView<Channel> t) {
        List<Integer> sel = numbers(t);
        int where = sel.isEmpty() ? t.getItems().get(0).number() : sel.get(0);
        vm.insertRow(where);
    }

    private void copyToClipboard(TableView<Channel> t) {
        ClipboardContent content = new ClipboardContent();
        content.putString(vm.copyRows(numbers(t)));
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void pasteFromClipboard(TableView<Channel> t) {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null) {
            return;
        }
        List<Integer> sel = numbers(t);
        int first = sel.isEmpty() ? t.getItems().get(0).number() : sel.get(0);
        vm.pasteRows(first, text);
    }

    private void moveOrCopy(boolean move) {
        List<Integer> sel = numbers(currentTable());
        if (sel.isEmpty()) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(Integer.toString(sel.get(0)));
        dialog.setTitle(messages.get(move ? "db.moveRows" : "db.copyRows"));
        dialog.setHeaderText(null);
        dialog.setContentText(messages.get(move ? "db.moveTo" : "db.copyTo"));
        style(dialog);
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }
        try {
            int dest = Integer.parseInt(answer.get().strip());
            if (move) {
                vm.moveRows(sel, dest);
            } else {
                vm.copyRowsTo(sel, dest);
            }
        } catch (NumberFormatException e) {
            vm.message().set(messages.get("db.destination", answer.get()));
        }
    }

    private void sort() {
        int bank = currentBank();
        List<String> choices = List.of(messages.get("db.sortFrequency"), messages.get("db.sortTag"));
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle(messages.get("db.sort"));
        dialog.setHeaderText(null);
        dialog.setContentText(messages.get("db.sortBy", bank));
        style(dialog);
        dialog.showAndWait().ifPresent(choice -> vm.sortBank(bank,
                choice.equals(choices.get(0)) ? SortKey.FREQUENCY : SortKey.TAG));
    }

    private void findReplace() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(messages.get("db.findReplace"));
        TextField find = new TextField();
        TextField replace = new TextField();
        CheckBox matchCase = new CheckBox(messages.get("db.matchCase"));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label(messages.get("db.find")), find);
        grid.addRow(1, new Label(messages.get("db.replaceWith")), replace);
        grid.add(matchCase, 1, 2);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        style(dialog);
        dialog.showAndWait().filter(b -> b == ButtonType.OK)
                .ifPresent(b -> vm.findReplace(find.getText(), replace.getText(), matchCase.isSelected()));
    }

    private void findDuplicates() {
        List<List<Integer>> groups = vm.findDuplicates();
        if (groups.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (List<Integer> g : groups) {
            sb.append(messages.get("db.duplicateLine", String.join(", ", g.stream().map(String::valueOf).toList())))
                    .append('\n');
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(messages.get("db.duplicatesTitle"));
        alert.setHeaderText(null);
        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        alert.getDialogPane().setContent(area);
        style(alert);
        alert.showAndWait();
    }

    private void style(Dialog<?> dialog) {
        if (getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(getScene().getStylesheets());
        }
    }

    // ---- converters -------------------------------------------------------------------------

    /** Shows frequencies as {@code 0145.4750}; an unreadable text gives {@code null}. */
    private static final class FrequencyConverter extends StringConverter<BigDecimal> {
        @Override
        public String toString(BigDecimal f) {
            return f == null ? "" : String.format(Locale.ROOT, "%09.4f", f);
        }

        @Override
        public BigDecimal fromString(String text) {
            try {
                BigDecimal v = new BigDecimal(text.strip().replace(',', '.'));
                return v.signum() < 0 ? null : v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static final class ToneConverter extends StringConverter<Tone> {
        @Override
        public String toString(Tone t) {
            return t == null ? "" : t.label();
        }

        @Override
        public Tone fromString(String text) {
            return Tone.parse(text).orElse(Tone.OFF);
        }
    }
}
