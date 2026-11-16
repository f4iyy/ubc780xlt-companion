package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.protocol.ScannerKey;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.RemoteViewModel;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

/** The front-panel keys of the scanner. Click = press, hold for 1.5 s = long press. */
public final class KeypadView extends HBox {

    private static final long LONG_PRESS_MS = 1500;

    private final RemoteViewModel vm;

    public KeypadView(RemoteViewModel vm, Messages messages) {
        this.vm = vm;
        setSpacing(18);
        setAlignment(Pos.TOP_LEFT);
        setPadding(new Insets(0));

        GridPane functions = grid();
        functions.add(key("SCAN", ScannerKey.SCAN), 0, 0);
        functions.add(key("\u25B2 HOLD", ScannerKey.HOLD_UP), 1, 0);
        functions.add(key("MAN", ScannerKey.MAN), 0, 1);
        functions.add(key("SRCH", ScannerKey.SEARCH), 1, 1);
        functions.add(key("TRUNK", ScannerKey.TRUNK), 0, 2);
        functions.add(key("\u25BC LIMIT", ScannerKey.LIMIT_DOWN), 1, 2);
        functions.add(key("MENU", ScannerKey.MENU), 0, 3);
        functions.add(key("SELECT", ScannerKey.SELECT), 1, 3);
        functions.add(key("SELECT \u25CF", ScannerKey.SELECT_PUSH), 0, 4, 2, 1);

        GridPane numbers = grid();
        String[] ranges = {"1 - 50", "51 - 100", "101 - 150", "151 - 200", "201 - 250", "251 - 300", "301 - 350",
                "351 - 400", "401 - 450"};
        for (int i = 0; i < 9; i++) {
            numbers.add(numberKey(i + 1, ranges[i]), i % 3, i / 3);
        }
        numbers.add(numberKeyOf("RVRS .", ScannerKey.DOT, ""), 0, 3);
        numbers.add(numberKey(0, "451 - 500"), 1, 3);
        numbers.add(numberKeyOf("E / REM", ScannerKey.ENTER, ""), 2, 3);
        numbers.add(key("L/O", ScannerKey.LOCKOUT), 0, 4);
        numbers.add(key("PRI", ScannerKey.PRI), 1, 4);
        numbers.add(key("WX", ScannerKey.WEATHER), 2, 4);

        Label hint = new Label(messages.get("keypad.hint"));
        hint.getStyleClass().add("hint");
        getChildren().addAll(functions, new VBox(6, numbers, hint));
        disableProperty().bind(vm.connected().not());
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        return g;
    }

    private Button numberKey(int digit, String range) {
        return numberKeyOf(Integer.toString(digit), ScannerKey.digit(digit), range);
    }

    private Button numberKeyOf(String label, ScannerKey key, String range) {
        Button b = key(label, key);
        if (!range.isEmpty()) {
            b.setTooltip(new Tooltip(range));
        }
        return b;
    }

    private Button key(String label, ScannerKey key) {
        Button b = new Button(label);
        b.setMinWidth(78);
        b.setPrefWidth(78);
        b.getStyleClass().add("scanner-key");
        long[] pressedAt = new long[1];
        b.setOnMousePressed(e -> pressedAt[0] = System.currentTimeMillis());
        b.setOnMouseReleased(e -> {
            if (b.isHover()) {
                boolean hold = System.currentTimeMillis() - pressedAt[0] >= LONG_PRESS_MS;
                vm.pressKey(key, hold);
            }
        });
        b.setFocusTraversable(false); // no PC keyboard shortcuts
        return b;
    }
}
