package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.history.Reception;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.HistoryViewModel;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** The history tab: what the scanner received, live, with a filter and an export of the selection. */
public final class HistoryView extends BorderPane {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final HistoryViewModel vm;
    private final Messages messages;
    private final TableView<Reception> table = new TableView<>();

    public HistoryView(HistoryViewModel vm, Messages messages) {
        this.vm = vm;
        this.messages = messages;

        CheckBox record = new CheckBox(messages.get("history.record"));
        record.selectedProperty().bindBidirectional(vm.recording());

        TextField filter = new TextField();
        filter.setPromptText(messages.get("history.filter"));
        filter.setPrefWidth(220);
        HBox.setHgrow(filter, Priority.ALWAYS);

        Button clear = new Button(messages.get("history.clear"));
        clear.setOnAction(e -> vm.clear());
        Button export = new Button(messages.get("history.export"));
        export.setOnAction(e -> export());

        HBox bar = new HBox(10, record, filter, clear, export);
        bar.setPadding(new Insets(10, 0, 10, 0));
        setTop(bar);

        FilteredList<Reception> shown = new FilteredList<>(vm.receptions(), r -> true);
        filter.textProperty().addListener((obs, old, now) -> shown.setPredicate(r -> matches(r, now)));
        table.setItems(shown);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Reception item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("row-live");
                if (!empty && item != null && item.inProgress()) {
                    getStyleClass().add("row-live");
                }
            }
        });
        table.getColumns().addAll(List.of(
                column("Time", 90, r -> TIME.format(r.start().atZone(ZoneId.systemDefault()))),
                column("Duration (s)", 100, this::duration),
                column("Bank", 60, r -> r.bank() == 0 ? "" : Integer.toString(r.bank())),
                column("Channel", 70, r -> r.channel() == 0 ? "" : Integer.toString(r.channel())),
                column("Frequency", 100, Reception::frequency),
                column("Text Tag", 160, Reception::tag),
                column("Mod", 60, Reception::modulation),
                column("Tone", 90, Reception::tone),
                column("Signal Max", 90, r -> Integer.toString(r.maxSignal()))));
        table.setPlaceholder(new javafx.scene.control.Label(messages.get("history.empty")));
        setCenter(table);
        setPadding(new Insets(0, 14, 10, 14));
    }

    private String duration(Reception r) {
        return r.inProgress() ? "…" : String.format(Locale.ROOT, "%.1f", r.duration().toMillis() / 1000.0);
    }

    private static boolean matches(Reception r, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String needle = text.strip().toLowerCase(Locale.ROOT);
        return (r.tag() + " " + r.frequency() + " " + r.channel() + " " + r.modulation() + " " + r.tone())
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private static TableColumn<Reception, String> column(String title, double width, Function<Reception, String> text) {
        TableColumn<Reception, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(text.apply(d.getValue())));
        c.setPrefWidth(width);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        return c;
    }

    private void export() {
        List<Reception> selection = table.getSelectionModel().getSelectedItems().isEmpty()
                ? List.copyOf(table.getItems())
                : List.copyOf(table.getSelectionModel().getSelectedItems());
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("db.xlsx"), "*.xlsx"));
        fc.setInitialFileName("history.xlsx");
        Window owner = getScene() == null ? null : getScene().getWindow();
        File f = fc.showSaveDialog(owner);
        if (f != null) {
            vm.export(selection, f.toPath());
        }
    }
}
