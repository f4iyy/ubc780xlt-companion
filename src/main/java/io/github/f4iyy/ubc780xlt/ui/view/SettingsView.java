package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.Backlight;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.CtcssFunction;
import io.github.f4iyy.ubc780xlt.settings.ScannerSettings.MuteMode;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.SettingsViewModel;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** The scanner settings tab: global settings, bank selection and the search skip memory. */
public final class SettingsView extends VBox {

    private final Messages messages;

    public SettingsView(SettingsViewModel vm, Messages messages) {
        this.messages = messages;
        setSpacing(14);
        setPadding(new Insets(14));

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(10);
        int row = 0;
        form.addRow(row++, new Label(messages.get("settings.backlight")),
                choice(vm.backlight(), Backlight.values(), b -> "settings.backlight." + b.code()));
        form.addRow(row++, new Label(messages.get("settings.mute")),
                choice(vm.mute(), MuteMode.values(), m -> "settings.mute." + m.code()));
        form.addRow(row++, new Label(messages.get("settings.ctcss")),
                choice(vm.ctcss(), CtcssFunction.values(), c -> "settings.ctcss." + c.code()));
        form.addRow(row++, new Label(messages.get("settings.beep")), check(vm.beep()));
        form.addRow(row++, new Label(messages.get("settings.priority")), check(vm.priority()));
        form.addRow(row++, new Label(messages.get("settings.dataSkip")), check(vm.dataSkip()));
        form.addRow(row++, new Label(messages.get("settings.editLock")), check(vm.editLock()));
        form.addRow(row, new Label(messages.get("settings.screenMask")), check(vm.screenMask()));

        VBox banks = new VBox(8,
                new Label(messages.get("settings.scanBanks")), bankRow(vm, true),
                new Label(messages.get("settings.searchBanks")), bankRow(vm, false));

        ListView<String> skip = new ListView<>(vm.skipList());
        skip.setPrefHeight(150);
        TextField add = new TextField();
        add.setPromptText("466.4500");
        Button addButton = new Button(messages.get("settings.skipAdd"));
        Runnable doAdd = () -> {
            vm.addSkip(add.getText());
            add.clear();
        };
        addButton.setOnAction(e -> doAdd.run());
        add.setOnAction(e -> doAdd.run());
        Label hint = new Label(messages.get("settings.skipHint"));
        hint.getStyleClass().add("hint");
        VBox skipBox = new VBox(8, new Label(messages.get("settings.skip")), skip, new HBox(8, add, addButton), hint);
        HBox.setHgrow(skipBox, Priority.ALWAYS);

        Button reload = new Button(messages.get("settings.reload"));
        reload.setOnAction(e -> vm.load());
        Label note = new Label(messages.get("settings.volumeNote"));
        note.getStyleClass().add("hint");

        VBox content = new VBox(14, form, banks, skipBox);
        content.disableProperty().bind(vm.connected().not());
        getChildren().addAll(new HBox(12, reload, note), content);
    }

    private <E> ChoiceBox<E> choice(ObjectProperty<E> property, E[] values, Function<E, String> key) {
        ChoiceBox<E> box = new ChoiceBox<>();
        box.getItems().addAll(values);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : messages.get(key.apply(value));
            }

            @Override
            public E fromString(String text) {
                return null;
            }
        });
        box.valueProperty().bindBidirectional(property);
        return box;
    }

    private static CheckBox check(javafx.beans.property.BooleanProperty property) {
        CheckBox box = new CheckBox();
        box.selectedProperty().bindBidirectional(property);
        return box;
    }

    private static HBox bankRow(SettingsViewModel vm, boolean scan) {
        HBox row = new HBox(14);
        for (int bank = 1; bank <= 10; bank++) {
            CheckBox box = new CheckBox(Integer.toString(bank));
            box.selectedProperty().bindBidirectional(scan ? vm.scanBank(bank) : vm.searchBank(bank));
            row.getChildren().add(box);
        }
        return row;
    }
}
