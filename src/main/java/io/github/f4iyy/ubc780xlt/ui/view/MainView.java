package io.github.f4iyy.ubc780xlt.ui.view;

import io.github.f4iyy.ubc780xlt.AppConfig;
import io.github.f4iyy.ubc780xlt.AppInfo;
import io.github.f4iyy.ubc780xlt.AppPaths;
import io.github.f4iyy.ubc780xlt.protocol.ConnectionState;
import io.github.f4iyy.ubc780xlt.ui.Messages;
import io.github.f4iyy.ubc780xlt.ui.Theme;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.DatabaseViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.HistoryViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.MainViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.RemoteViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.SettingsViewModel;
import io.github.f4iyy.ubc780xlt.ui.viewmodel.TransferViewModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ButtonType;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Main window: menus, connection toolbar, status bar. Widget wiring only; logic is in {@link MainViewModel}. */
public final class MainView {

    private final MainViewModel vm;
    private final RemoteViewModel remote;
    private final DatabaseViewModel database;
    private final TransferViewModel transfer;
    private final HistoryViewModel history;
    private final SettingsViewModel settings;
    private Stage stage;
    private static final double LCD_MAX_WIDTH = 540;

    private LcdView lcdView;
    private final BooleanProperty showRemote;
    private final Messages messages;
    private final AppConfig config;
    private final Scene scene;

    public MainView(MainViewModel vm, RemoteViewModel remote, DatabaseViewModel database, TransferViewModel transfer,
            HistoryViewModel history, SettingsViewModel settings, Messages messages, AppConfig config) {
        this.vm = vm;
        this.history = history;
        this.settings = settings;
        this.transfer = transfer;
        this.remote = remote;
        this.database = database;
        this.messages = messages;
        this.config = config;
        this.showRemote = new SimpleBooleanProperty(config.remotePanel());

        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolbar()));
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        scene = new Scene(root, 1040, 900);
        applyTheme(config.theme());
    }

    public void show(Stage stage) {
        this.stage = stage;
        updateTitle();
        database.file().addListener((obs, old, now) -> updateTitle());
        database.modified().addListener((obs, old, now) -> updateTitle());
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscard()) {
                e.consume();
            }
        });
        stage.show();
    }

    private void updateTitle() {
        Path file = database.file().get();
        String name = file == null ? "" : " - " + file.getFileName();
        String star = database.modified().get() ? " *" : "";
        stage.setTitle(messages.get("app.title") + name + star);
    }

    /** True if there is nothing to lose, or the user accepts losing the unsaved changes. */
    private boolean confirmDiscard() {
        if (!database.modified().get()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, messages.get("db.unsaved"), ButtonType.OK,
                ButtonType.CANCEL);
        alert.setTitle(messages.get("db.unsavedTitle"));
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().setAll(scene.getStylesheets());
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private FileChooser chooser() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("db.xlsx"), "*.xlsx"));
        Path current = database.file().get();
        if (current != null && current.getParent() != null) {
            fc.setInitialDirectory(current.getParent().toFile());
        }
        return fc;
    }

    private void openFile() {
        if (!confirmDiscard()) {
            return;
        }
        File f = chooser().showOpenDialog(stage);
        if (f != null) {
            database.open(f.toPath());
        }
    }

    private void saveFile() {
        if (database.file().get() == null) {
            saveFileAs();
        } else {
            database.save();
        }
    }

    private void saveFileAs() {
        FileChooser fc = chooser();
        fc.setInitialFileName("scanner.xlsx");
        File f = fc.showSaveDialog(stage);
        if (f != null) {
            Path path = f.toPath();
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                path = path.resolveSibling(path.getFileName() + ".xlsx");
            }
            database.saveAs(path);
        }
    }

    private void applyTheme(Theme theme) {
        scene.getStylesheets().setAll(theme.stylesheets());
    }

    private MenuBar buildMenuBar() {
        MenuItem exit = new MenuItem(messages.get("menu.exit"));
        exit.setOnAction(e -> {
            if (confirmDiscard()) {
                Platform.exit();
            }
        });
        MenuItem newItem = new MenuItem(messages.get("menu.new"));
        newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        newItem.setOnAction(e -> {
            if (confirmDiscard()) {
                database.newProgramming();
            }
        });
        MenuItem openItem = new MenuItem(messages.get("menu.open"));
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openItem.setOnAction(e -> openFile());
        MenuItem saveItem = new MenuItem(messages.get("menu.save"));
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveItem.setOnAction(e -> saveFile());
        MenuItem saveAsItem = new MenuItem(messages.get("menu.saveAs"));
        saveAsItem.setOnAction(e -> saveFileAs());
        Menu file = new Menu(messages.get("menu.file"), null, newItem, openItem, saveItem, saveAsItem,
                new SeparatorMenuItem(), exit);

        MenuItem undoItem = new MenuItem(messages.get("db.undo"));
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        undoItem.setOnAction(e -> database.undo());
        undoItem.disableProperty().bind(database.history().canUndo().not());
        MenuItem redoItem = new MenuItem(messages.get("db.redo"));
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        redoItem.setOnAction(e -> database.redo());
        redoItem.disableProperty().bind(database.history().canRedo().not());
        Menu edit = new Menu(messages.get("menu.edit"), null, undoItem, redoItem);

        ToggleGroup themes = new ToggleGroup();
        Menu themeMenu = new Menu(messages.get("menu.theme"));
        for (Theme t : Theme.values()) {
            RadioMenuItem item = new RadioMenuItem(messages.get("theme." + t.id()));
            item.setToggleGroup(themes);
            item.setSelected(t == config.theme());
            item.setOnAction(e -> {
                config.setTheme(t);
                applyTheme(t);
                saveConfig();
            });
            themeMenu.getItems().add(item);
        }

        ToggleGroup languages = new ToggleGroup();
        Menu languageMenu = new Menu(messages.get("menu.language"));
        for (String lang : new String[] {"en", "fr"}) {
            RadioMenuItem item = new RadioMenuItem(messages.get("lang." + lang));
            item.setToggleGroup(languages);
            item.setSelected(lang.equals(config.language()));
            item.setOnAction(e -> {
                config.setLanguage(lang);
                saveConfig();
                vm.message().set(messages.get("status.restartNeeded"));
            });
            languageMenu.getItems().add(item);
        }
        Menu options = new Menu(messages.get("menu.options"), null, themeMenu, languageMenu);

        CheckMenuItem remoteItem = new CheckMenuItem(messages.get("menu.showRemote"));
        remoteItem.selectedProperty().bindBidirectional(showRemote);
        showRemote.addListener((obs, old, now) -> {
            config.setRemotePanel(now);
            saveConfig();
        });
        Menu view = new Menu(messages.get("menu.view"), null, remoteItem);

        MenuItem about = new MenuItem(messages.get("menu.about"));
        about.setOnAction(e -> showAbout());
        Menu help = new Menu(messages.get("menu.help"), null, about);

        return new MenuBar(file, edit, view, options, help);
    }

    private HBox buildToolbar() {
        Label portLabel = new Label(messages.get("port.label"));
        ComboBox<String> ports = new ComboBox<>(vm.ports());
        ports.setPrefWidth(180);
        ports.valueProperty().bindBidirectional(vm.selectedPort());

        Button refresh = new Button(messages.get("button.refresh"));
        refresh.setOnAction(e -> vm.refreshPorts());

        Button scan = new Button(messages.get("button.scan"));
        scan.setOnAction(e -> vm.scan());
        scan.disableProperty().bind(vm.busy());

        Button connect = new Button();
        connect.textProperty().bind(Bindings.createStringBinding(
                () -> isLinked(vm.state().get()) ? messages.get("button.disconnect") : messages.get("button.connect"),
                vm.state()));
        connect.setOnAction(e -> {
            if (isLinked(vm.state().get())) {
                vm.disconnect();
            } else {
                vm.connect();
            }
        });
        connect.disableProperty().bind(vm.busy());

        HBox box = new HBox(portLabel, ports, refresh, scan, connect);
        box.getStyleClass().add("toolbar-box");
        return box;
    }

    private static boolean isLinked(ConnectionState s) {
        return s == ConnectionState.CONNECTED || s == ConnectionState.LOST || s == ConnectionState.RECONNECTING;
    }

    /** The remote panel (display, keypad, tune) always on top, the data tabs below. */
    private VBox buildCenter() {
        HBox remotePanel = buildRemotePanel();
        remotePanel.visibleProperty().bind(showRemote);
        remotePanel.managedProperty().bind(showRemote);

        Tab databaseTab = new Tab(messages.get("tab.database"), new DatabaseView(database, transfer, messages));
        Tab historyTab = new Tab(messages.get("tab.history"), new HistoryView(history, messages));
        Tab settingsTab = new Tab(messages.get("tab.settings"), new SettingsView(settings, messages));
        databaseTab.setClosable(false);
        historyTab.setClosable(false);
        settingsTab.setClosable(false);
        TabPane tabs = new TabPane(databaseTab, historyTab, settingsTab);

        VBox center = new VBox(remotePanel, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return center;
    }

    /**
     * Display on the left with the frequency box under it, keypad on the right. The group is centred and the display
     * stops growing at {@link #LCD_MAX_WIDTH}, so a wide window leaves margins instead of stretching the keypad.
     */
    private HBox buildRemotePanel() {
        lcdView = new LcdView();
        lcdView.stateProperty().bind(remote.lcd());
        lcdView.setMaxWidth(LCD_MAX_WIDTH);

        VBox left = new VBox(10, lcdView, new TuneView(remote, messages));
        left.setMaxWidth(LCD_MAX_WIDTH);
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox panel = new HBox(18, left, new KeypadView(remote, messages));
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(10, 14, 12, 14));
        panel.getStyleClass().add("remote-panel");
        // The tabs below give way when the window is short; the display keeps its size.
        panel.setMinHeight(Region.USE_PREF_SIZE);
        return panel;
    }

    /** Stops the display animation. */
    public void dispose() {
        if (lcdView != null) {
            lcdView.dispose();
        }
    }

    private HBox buildStatusBar() {
        Label stateLabel = new Label();
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> messages.get("state." + vm.state().get().name().toLowerCase(Locale.ROOT)), vm.state()));
        vm.state().addListener((obs, old, now) -> styleState(stateLabel, now));
        styleState(stateLabel, vm.state().get());

        Label info = new Label();
        info.textProperty().bind(vm.scannerInfo());

        Label msg = new Label();
        msg.textProperty().bind(vm.message());
        // The most recent message wins, whichever tab produced it.
        database.message().addListener((obs, old, now) -> vm.message().set(now));
        history.message().addListener((obs, old, now) -> vm.message().set(now));
        settings.message().addListener((obs, old, now) -> vm.message().set(now));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(stateLabel, info, spacer, msg);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private static void styleState(Label label, ConnectionState s) {
        label.getStyleClass().removeIf(c -> c.startsWith("state-"));
        label.getStyleClass().add("state-" + s.name().toLowerCase(Locale.ROOT));
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(messages.get("about.title"));
        alert.setHeaderText(AppInfo.NAME + " " + AppInfo.version());
        String logs = System.getProperty(AppPaths.LOG_DIR_PROPERTY, AppPaths.logDir().toString());
        String details = messages.get("about.details", System.getProperty("java.version"),
                System.getProperty("javafx.runtime.version", "?"), AppPaths.configFile(), logs);
        alert.setContentText(messages.get("about.text") + "\n\n" + details);
        alert.getDialogPane().getStylesheets().setAll(scene.getStylesheets());
        alert.showAndWait();
    }

    private void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            vm.message().set(messages.get("status.error", e.getMessage()));
        }
    }
}
