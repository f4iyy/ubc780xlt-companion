package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.model.Modulation;
import io.github.f4iyy.ubc780xlt.model.Step;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.RemoteViewModel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** "Frequency / Tune": type a frequency and tune the scanner to it without storing it. */
public final class TuneView extends VBox {

    public TuneView(RemoteViewModel vm, Messages messages) {
        setSpacing(6);

        TextField frequency = new TextField();
        frequency.setPromptText("145.4750");
        frequency.setPrefWidth(110);

        ComboBox<Step> step = new ComboBox<>();
        step.getItems().addAll(Step.values());
        step.setValue(Step.AUTO);

        ComboBox<Modulation> modulation = new ComboBox<>();
        modulation.getItems().addAll(Modulation.values());
        modulation.setValue(Modulation.AUTO);

        Button tune = new Button(messages.get("tune.button"));
        Runnable action = () -> vm.tune(frequency.getText(), step.getValue(), modulation.getValue());
        tune.setOnAction(e -> action.run());
        frequency.setOnAction(e -> action.run());

        FlowPane row = new FlowPane(8, 6,
                new Label(messages.get("tune.frequency")), frequency,
                new Label(messages.get("tune.step")), step,
                new Label(messages.get("tune.modulation")), modulation,
                tune);
        row.setAlignment(Pos.CENTER_LEFT);

        Label status = new Label();
        status.textProperty().bind(vm.tuneMessage());
        status.getStyleClass().add("hint");

        getChildren().addAll(row, status);
        disableProperty().bind(vm.connected().not());
    }
}
