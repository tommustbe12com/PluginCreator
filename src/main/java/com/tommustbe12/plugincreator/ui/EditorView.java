package com.tommustbe12.plugincreator.ui;

import com.tommustbe12.plugincreator.build.GradleBuildService;
import com.tommustbe12.plugincreator.events.EventCatalogService;
import com.tommustbe12.plugincreator.generator.GeneratedProject;
import com.tommustbe12.plugincreator.generator.PluginGenerator;
import com.tommustbe12.plugincreator.model.CommandAction;
import com.tommustbe12.plugincreator.model.FlowProgram;
import com.tommustbe12.plugincreator.model.GuiScreen;
import com.tommustbe12.plugincreator.model.GuiSlot;
import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginEventHandler;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EditorView {
    private final ProjectStorage storage;
    private final PluginGenerator generator = new PluginGenerator();
    private final GradleBuildService buildService = new GradleBuildService();
    private final EventCatalogService eventCatalog = new EventCatalogService();
    private final MaterialIconService materialIcons = new MaterialIconService();
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plugincreator-bg");
        t.setDaemon(true);
        return t;
    });

    private PluginProject model;
    private Path currentJsonPath;
    private GeneratedProject lastGenerated;

    private final BorderPane root = new BorderPane();
    private final TreeView<String> tree = new TreeView<>();
    private final TextArea jsonEditor = new TextArea();
    private final TextArea logArea = new TextArea();

    private final TextField nameField = new TextField();
    private final TextField versionField = new TextField();
    private final TextField groupField = new TextField();
    private final TextField mainClassField = new TextField();

    private final TableView<PluginCommand> commandsTable = new TableView<>();
    private final TableView<PluginEventHandler> eventsTable = new TableView<>();
    private final ListView<CommandAction> commandActionsList = new ListView<>();
    private final TextField cmdNameField = new TextField();
    private final TextField cmdDescField = new TextField();
    private boolean syncingCommandEditor;
    private final FlowEditorPane commandFlowEditor = new FlowEditorPane();
    private final ScratchBlockEditor scratchEditor = new ScratchBlockEditor();

    private final TreeView<Path> filesTree = new TreeView<>();
    private final CodeArea codeArea = new CodeArea();
    private Path openFilePath;

    private final TextField eventSearchField = new TextField();
    private final ListView<EventCatalogService.BukkitEventInfo> eventPickerList = new ListView<>();
    private List<EventCatalogService.BukkitEventInfo> cachedEvents = List.of();
    private final ComboBox<EventCatalogService.BukkitEventInfo> eventCombo = new ComboBox<>();
    private final FlowEditorPane eventFlowEditor = new FlowEditorPane();
    private final Label eventSelectedLabel = new Label("No event selected");

    private final ComboBox<GuiScreen> guiPicker = new ComboBox<>();
    private final Spinner<Integer> guiRowsSpinner = new Spinner<>(1, 6, 3);
    private final TextField guiTitleField = new TextField();
    private final TilePane guiGrid = new TilePane();
    private final ListView<CommandAction> guiClickActions = new ListView<>();
    private GuiSlot selectedGuiSlot;
    private final ComboBox<String> guiMaterialPicker = new ComboBox<>();

    public EditorView(ProjectStorage storage, PluginProject initialModel) {
        this.storage = storage;
        this.model = initialModel;
        initUi();
        refreshAllFromModel();
    }

    public Parent root() {
        return root;
    }

    public void shutdown() {
        background.shutdownNow();
    }

    private void initUi() {
        root.getStyleClass().add("app-shell");
        root.setTop(buildHeader());

        SplitPane split = new SplitPane();
        split.getItems().addAll(buildLeftPanel(), buildRightPanel());
        split.setDividerPositions(0.25);
        root.setCenter(split);

        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.getStyleClass().add("log-area");
        root.setBottom(logArea);
        BorderPane.setMargin(logArea, new Insets(8));
    }

    private Parent buildHeader() {
        VBox header = new VBox(6);
        header.getStyleClass().add("header");

        Label title = new Label("PluginCreator");
        title.getStyleClass().add("header-title");
        Label subtitle = new Label("Beta: edit JSON → generate → build (foundation for future UX)");
        subtitle.getStyleClass().add("header-subtitle");

        ToolBar toolbar = buildToolbar();
        header.getChildren().addAll(title, subtitle, toolbar);
        return header;
    }

    private ToolBar buildToolbar() {
        Button newBtn = new Button("New Project...");
        newBtn.setOnAction(e -> newProjectWizard());

        Button openBtn = new Button("Open JSON...");
        openBtn.setOnAction(e -> openJson());

        Button saveBtn = new Button("Save JSON");
        saveBtn.setOnAction(e -> saveJson(false));

        Button saveAsBtn = new Button("Save JSON As...");
        saveAsBtn.setOnAction(e -> saveJson(true));

        Button generateBtn = new Button("Generate...");
        generateBtn.setOnAction(e -> generate());
        generateBtn.getStyleClass().add("primary");

        Button buildBtn = new Button("Build");
        buildBtn.setOnAction(e -> build());
        buildBtn.getStyleClass().add("primary");

        Button openOutBtn = new Button("Open Output");
        openOutBtn.setOnAction(e -> openGeneratedRoot());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new ToolBar(newBtn, new Separator(), openBtn, saveBtn, saveAsBtn, openOutBtn, spacer, generateBtn, buildBtn);
    }

    private Parent buildLeftPanel() {
        tree.setShowRoot(true);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            log("Selected: " + newV.getValue());
        });
        VBox box = new VBox(10, new Label("Project Tree"), tree);
        box.getStyleClass().add("card");
        VBox.setVgrow(tree, Priority.ALWAYS);
        return box;
    }

    private Parent buildRightPanel() {
        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("Settings", buildSettingsPane()));
        tabs.getTabs().add(new Tab("Commands", buildCommandsPane()));
        tabs.getTabs().add(new Tab("Events", buildEventsPane()));
        tabs.getTabs().add(new Tab("Config", buildConfigPane()));
        tabs.getTabs().add(new Tab("GUI", buildGuiPane()));
        tabs.getTabs().add(new Tab("Files", buildFilesPane()));
        tabs.getTabs().add(new Tab("JSON (manual)", buildJsonPane()));
        tabs.getTabs().forEach(t -> t.setClosable(false));
        tabs.getStyleClass().add("card");
        return tabs;
    }

    private Parent buildSettingsPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        int row = 0;
        grid.addRow(row++, new Label("Plugin name"), nameField);
        grid.addRow(row++, new Label("Version"), versionField);
        grid.addRow(row++, new Label("Group / base package"), groupField);
        grid.addRow(row++, new Label("Main class"), mainClassField);

        nameField.textProperty().addListener((o, a, b) -> {
            model.setName(b);
            refreshJsonFromModel();
            refreshTree();
        });
        versionField.textProperty().addListener((o, a, b) -> {
            model.setVersion(b);
            refreshJsonFromModel();
        });
        groupField.textProperty().addListener((o, a, b) -> {
            model.setGroupId(b);
            refreshJsonFromModel();
        });
        mainClassField.textProperty().addListener((o, a, b) -> {
            model.setMainClassName(b);
            refreshJsonFromModel();
        });

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(160);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c1, c2);
        return grid;
    }

    private Parent buildCommandsPane() {
        commandsTable.getColumns().clear();
        TableColumn<PluginCommand, String> nameCol = new TableColumn<>("Command");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("/" + c.getValue().getName()));
        nameCol.setPrefWidth(240);

        TableColumn<PluginCommand, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
        descCol.setPrefWidth(520);

        commandsTable.getColumns().addAll(nameCol, descCol);
        commandsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        commandsTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showCommand(b));

        Button add = new Button("Add command");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> addCommand());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> removeCommand());

        HBox actions = new HBox(8, add, remove);
        actions.setPadding(new Insets(10, 0, 0, 0));

        VBox left = new VBox(10, new Label("Commands"), commandsTable, actions);
        VBox.setVgrow(commandsTable, Priority.ALWAYS);
        left.getStyleClass().add("subcard");
        left.setPadding(new Insets(12));

        Parent editor = buildCommandFlowEditor();
        SplitPane split = new SplitPane(left, editor);
        split.setDividerPositions(0.48);
        return new StackPane(split);
    }

    private Parent buildCommandFlowEditor() {
        Label title = new Label("Command blocks");
        title.getStyleClass().add("section-title");

        cmdNameField.setPromptText("hello");
        cmdDescField.setPromptText("Says hello");

        cmdNameField.textProperty().addListener((o, a, b) -> {
            if (syncingCommandEditor) return;
            PluginCommand selected = commandsTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            selected.setName(b);
            refreshTree();
            commandsTable.refresh();
            refreshJsonFromModel();
        });
        cmdDescField.textProperty().addListener((o, a, b) -> {
            if (syncingCommandEditor) return;
            PluginCommand selected = commandsTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            selected.setDescription(b);
            commandsTable.refresh();
            refreshJsonFromModel();
        });

        scratchEditor.setOnChange(this::refreshJsonFromModel);

        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(10);
        fields.addRow(0, new Label("Name"), cmdNameField);
        fields.addRow(1, new Label("Description"), cmdDescField);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(120);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().setAll(c1, c2);

        VBox box = new VBox(12, title, fields, scratchEditor.root());
        VBox.setVgrow(scratchEditor.root(), Priority.ALWAYS);
        box.getStyleClass().add("subcard");
        box.setPadding(new Insets(12));
        return box;
    }

    private void showCommand(PluginCommand cmd) {
        syncingCommandEditor = true;
        try {
            if (cmd == null) {
                cmdNameField.setText("");
                cmdDescField.setText("");
                commandFlowEditor.setProgram(new FlowProgram());
                scratchEditor.setProgram(new com.tommustbe12.plugincreator.model.ScratchProgram());
                return;
            }
            cmdNameField.setText(cmd.getName());
            cmdDescField.setText(cmd.getDescription());
            commandFlowEditor.setProgram(cmd.getProgram());
            scratchEditor.setProgram(cmd.getScratch());
        } finally {
            syncingCommandEditor = false;
        }
    }

    private void addCommandAction() {
        PluginCommand cmd = commandsTable.getSelectionModel().getSelectedItem();
        if (cmd == null) return;
        ChoiceDialog<CommandAction.Type> typeDialog = new ChoiceDialog<>(CommandAction.Type.SEND_MESSAGE, CommandAction.Type.values());
        typeDialog.setHeaderText("Choose action type");
        var type = typeDialog.showAndWait();
        if (type.isEmpty()) return;
        CommandAction action = new CommandAction();
        action.setType(type.get());

        if (type.get() == CommandAction.Type.GIVE_ITEM) {
            TextInputDialog mat = new TextInputDialog("DIAMOND");
            mat.setHeaderText("Material (e.g. DIAMOND)");
            var m = mat.showAndWait();
            if (m.isEmpty()) return;
            action.setMaterial(m.get().trim());

            TextInputDialog amt = new TextInputDialog("1");
            amt.setHeaderText("Amount");
            var a = amt.showAndWait();
            if (a.isEmpty()) return;
            try { action.setAmount(Integer.parseInt(a.get().trim())); } catch (Exception ignored) { action.setAmount(1); }
        } else if (type.get() == CommandAction.Type.TELEPORT_SELF) {
            TextInputDialog world = new TextInputDialog("world");
            world.setHeaderText("World name");
            var w = world.showAndWait();
            if (w.isEmpty()) return;
            action.setWorld(w.get().trim());

            TextInputDialog x = new TextInputDialog("0");
            x.setHeaderText("X");
            var xs = x.showAndWait();
            if (xs.isEmpty()) return;
            TextInputDialog y = new TextInputDialog("100");
            y.setHeaderText("Y");
            var ys = y.showAndWait();
            if (ys.isEmpty()) return;
            TextInputDialog z = new TextInputDialog("0");
            z.setHeaderText("Z");
            var zs = z.showAndWait();
            if (zs.isEmpty()) return;
            try { action.setX(Double.parseDouble(xs.get().trim())); } catch (Exception ignored) {}
            try { action.setY(Double.parseDouble(ys.get().trim())); } catch (Exception ignored) {}
            try { action.setZ(Double.parseDouble(zs.get().trim())); } catch (Exception ignored) {}
        } else if (type.get() == CommandAction.Type.SET_GAMEMODE) {
            TextInputDialog gm = new TextInputDialog("SURVIVAL");
            gm.setHeaderText("Gamemode (SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR)");
            var g = gm.showAndWait();
            if (g.isEmpty()) return;
            action.setGamemode(g.get().trim().toUpperCase());
        } else {
            TextInputDialog textDialog = new TextInputDialog("Hello!");
            textDialog.setHeaderText("Action text");
            var text = textDialog.showAndWait();
            if (text.isEmpty()) return;
            action.setText(text.get());
        }

        cmd.getActions().add(action);
        commandActionsList.getItems().setAll(cmd.getActions());
        refreshJsonFromModel();
    }

    private void editSelectedAction() {
        PluginCommand cmd = commandsTable.getSelectionModel().getSelectedItem();
        CommandAction selected = commandActionsList.getSelectionModel().getSelectedItem();
        if (cmd == null || selected == null) return;

        ChoiceDialog<CommandAction.Type> typeDialog = new ChoiceDialog<>(selected.getType(), CommandAction.Type.values());
        typeDialog.setHeaderText("Action type");
        var type = typeDialog.showAndWait();
        if (type.isEmpty()) return;

        TextInputDialog textDialog = new TextInputDialog(selected.getText());
        textDialog.setHeaderText("Action text");
        var text = textDialog.showAndWait();
        if (text.isEmpty()) return;

        selected.setType(type.get());
        selected.setText(text.get());
        commandActionsList.refresh();
        refreshJsonFromModel();
    }

    private void removeSelectedAction() {
        PluginCommand cmd = commandsTable.getSelectionModel().getSelectedItem();
        CommandAction selected = commandActionsList.getSelectionModel().getSelectedItem();
        if (cmd == null || selected == null) return;
        cmd.getActions().remove(selected);
        commandActionsList.getItems().setAll(cmd.getActions());
        refreshJsonFromModel();
    }

    private Parent buildEventsPane() {
        eventsTable.getColumns().clear();
        eventsTable.setEditable(true);

        TableColumn<PluginEventHandler, String> prettyCol = new TableColumn<>("Event");
        prettyCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(EventCatalogService.toDisplayName(simpleName(c.getValue().getEventClass()))));
        prettyCol.setPrefWidth(240);

        TableColumn<PluginEventHandler, String> eventCol = new TableColumn<>("Class (advanced)");
        eventCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEventClass()));
        eventCol.setPrefWidth(520);
        eventCol.setVisible(false);

        TableColumn<PluginEventHandler, String> methodCol = new TableColumn<>("Handler");
        methodCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMethodName()));
        methodCol.setCellFactory(TextFieldTableCell.forTableColumn());
        methodCol.setOnEditCommit(e -> {
            e.getRowValue().setMethodName(e.getNewValue());
            refreshJsonFromModel();
        });
        methodCol.setPrefWidth(180);

        TableColumn<PluginEventHandler, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMessage()));
        msgCol.setCellFactory(TextFieldTableCell.forTableColumn());
        msgCol.setOnEditCommit(e -> {
            e.getRowValue().setMessage(e.getNewValue());
            refreshJsonFromModel();
        });
        msgCol.setPrefWidth(280);

        eventsTable.getColumns().addAll(prettyCol, eventCol, methodCol, msgCol);
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventsTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showEventHandler(b));

        VBox picker = buildEventPicker();
        picker.getStyleClass().add("subcard");

        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> removeEvent());

        HBox actions = new HBox(8, remove);
        actions.setPadding(new Insets(10, 0, 0, 0));

        eventSelectedLabel.getStyleClass().add("header-subtitle");
        eventFlowEditor.setOnChange(this::refreshJsonFromModel);
        VBox flowBox = new VBox(10, new Label("Event blocks"), eventSelectedLabel, eventFlowEditor.root());
        flowBox.getStyleClass().add("subcard");
        flowBox.setPadding(new Insets(12));
        VBox.setVgrow(eventFlowEditor.root(), Priority.ALWAYS);

        VBox tableBox = new VBox(10, new Label("Added handlers"), eventsTable, actions);
        VBox.setVgrow(eventsTable, Priority.ALWAYS);
        tableBox.getStyleClass().add("subcard");
        tableBox.setPadding(new Insets(12));

        VBox box = new VBox(16, picker, tableBox, flowBox);
        box.setPadding(new Insets(12));
        return box;
    }

    private void showEventHandler(PluginEventHandler handler) {
        if (handler == null) {
            eventSelectedLabel.setText("No event selected");
            eventFlowEditor.setProgram(new FlowProgram());
            return;
        }
        eventSelectedLabel.setText(EventCatalogService.toDisplayName(simpleName(handler.getEventClass())));
        eventFlowEditor.setProgram(handler.getProgram());
    }

    private VBox buildEventPicker() {
        Label title = new Label("Event catalog");
        title.getStyleClass().add("section-title");

        eventSearchField.setPromptText("Search events (e.g. join, quit, block break)...");
        eventSearchField.textProperty().addListener((o, a, b) -> refreshEventPicker());

        eventCombo.setEditable(true);
        eventCombo.setPromptText("Type event name (e.g. On Player Join)...");
        eventCombo.setMaxWidth(Double.MAX_VALUE);
        eventCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EventCatalogService.BukkitEventInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        eventCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(EventCatalogService.BukkitEventInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.displayName());
            }
        });
        eventCombo.getEditor().textProperty().addListener((o, a, b) -> {
            if (b == null) return;
            refreshEventCombo(b);
            if (!eventCombo.isShowing()) eventCombo.show();
        });

        eventPickerList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EventCatalogService.BukkitEventInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.displayName());
                }
            }
        });
        eventPickerList.setPrefHeight(220);

        Button refresh = new Button("Refresh catalog");
        refresh.setOnAction(e -> loadEventCatalog());

        Button add = new Button("Add handler");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> addSelectedEventHandler());

        Button addTyped = new Button("Add typed");
        addTyped.getStyleClass().add("primary");
        addTyped.setOnAction(e -> addFromCombo());

        HBox comboRow = new HBox(10, eventCombo, addTyped);
        HBox.setHgrow(eventCombo, Priority.ALWAYS);

        HBox actions = new HBox(10, refresh, add);

        VBox box = new VBox(10, title, comboRow, new Label("Or browse/search"), eventSearchField, eventPickerList, actions);
        box.setPadding(new Insets(12));
        if (cachedEvents.isEmpty()) {
            loadEventCatalog();
        } else {
            refreshEventPicker();
        }
        return box;
    }

    private void loadEventCatalog() {
        log("Loading Bukkit/Paper event catalog...");
        background.submit(() -> {
            try {
                List<EventCatalogService.BukkitEventInfo> events = eventCatalog.listAllEvents();
                Platform.runLater(() -> {
                    cachedEvents = events;
                    refreshEventPicker();
                    refreshEventCombo("");
                    log("Loaded " + cachedEvents.size() + " events.");
                });
            } catch (Exception ex) {
                log("Event catalog failed: " + ex.getMessage());
            }
        });
    }

    private void refreshEventPicker() {
        String q = eventSearchField.getText();
        List<EventCatalogService.BukkitEventInfo> filtered = cachedEvents.stream()
                .filter(e -> e.matches(q))
                .limit(600)
                .collect(Collectors.toList());
        eventPickerList.getItems().setAll(filtered);
        if (!filtered.isEmpty() && eventPickerList.getSelectionModel().getSelectedItem() == null) {
            eventPickerList.getSelectionModel().select(0);
        }
    }

    private void refreshEventCombo(String query) {
        List<EventCatalogService.BukkitEventInfo> filtered = cachedEvents.stream()
                .filter(e -> e.matches(query))
                .limit(40)
                .collect(Collectors.toList());
        eventCombo.getItems().setAll(filtered);
        if (!filtered.isEmpty()) {
            eventCombo.getSelectionModel().select(0);
        }
    }

    private void addSelectedEventHandler() {
        EventCatalogService.BukkitEventInfo selected = eventPickerList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        PluginEventHandler handler = new PluginEventHandler();
        handler.setEventClass(selected.className());
        handler.setMethodName(selected.suggestedMethodName());
        handler.setMessage("Triggered: " + selected.displayName());
        model.getEvents().add(handler);
        refreshEventsTable();
        refreshTree();
        refreshJsonFromModel();
        eventsTable.getSelectionModel().select(handler);
    }

    private void addFromCombo() {
        EventCatalogService.BukkitEventInfo selected = eventCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            String typed = eventCombo.getEditor().getText();
            if (typed != null && !typed.isBlank()) {
                selected = cachedEvents.stream().filter(e -> e.displayName().equalsIgnoreCase(typed.trim())).findFirst().orElse(null);
            }
        }
        if (selected == null) {
            log("Pick a valid event from the dropdown.");
            return;
        }
        PluginEventHandler handler = new PluginEventHandler();
        handler.setEventClass(selected.className());
        handler.setMethodName(selected.suggestedMethodName());
        handler.setMessage("Triggered: " + selected.displayName());
        model.getEvents().add(handler);
        refreshEventsTable();
        refreshTree();
        refreshJsonFromModel();
        eventsTable.getSelectionModel().select(handler);
    }

    private static String simpleName(String fqcn) {
        if (fqcn == null) return "";
        int idx = fqcn.lastIndexOf('.');
        return (idx < 0) ? fqcn : fqcn.substring(idx + 1);
    }

    private Parent buildConfigPane() {
        TableView<Map.Entry<String, String>> table = new TableView<>();
        table.setEditable(true);

        TableColumn<Map.Entry<String, String>, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getKey()));
        keyCol.setCellFactory(TextFieldTableCell.forTableColumn());
        keyCol.setOnEditCommit(e -> {
            String oldKey = e.getRowValue().getKey();
            String newKey = e.getNewValue();
            if (newKey == null || newKey.isBlank()) return;
            String value = model.getConfig().getValues().remove(oldKey);
            model.getConfig().getValues().put(newKey.trim(), value);
            refreshConfigTable(table);
            refreshJsonFromModel();
        });

        TableColumn<Map.Entry<String, String>, String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getValue()));
        valCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valCol.setOnEditCommit(e -> {
            model.getConfig().getValues().put(e.getRowValue().getKey(), e.getNewValue());
            refreshJsonFromModel();
        });

        table.getColumns().addAll(keyCol, valCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button add = new Button("Add");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> {
            String base = "key";
            int i = 1;
            while (model.getConfig().getValues().containsKey(base + i)) i++;
            model.getConfig().getValues().put(base + i, "value");
            refreshConfigTable(table);
            refreshJsonFromModel();
        });

        Button remove = new Button("Remove Selected");
        remove.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            model.getConfig().getValues().remove(selected.getKey());
            refreshConfigTable(table);
            refreshJsonFromModel();
        });

        Button rawEdit = new Button("Raw editor...");
        rawEdit.setOnAction(e -> openRawConfigEditor());

        HBox actions = new HBox(10, add, remove, rawEdit);
        actions.setPadding(new Insets(12, 12, 0, 12));

        VBox box = new VBox(10, actions, table);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        Platform.runLater(() -> refreshConfigTable(table));
        return box;
    }

    private void refreshConfigTable(TableView<Map.Entry<String, String>> table) {
        table.getItems().setAll(new ArrayList<>(model.getConfig().getValues().entrySet()));
    }

    private void openRawConfigEditor() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Raw config editor");
        dialog.setHeaderText("Advanced: edit generated config.yml as raw text");
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextArea area = new TextArea();
        area.setWrapText(false);
        area.setText(model.getConfig().getRaw() == null ? "" : model.getConfig().getRaw());
        area.setPrefWidth(900);
        area.setPrefHeight(500);
        dialog.getDialogPane().setContent(area);

        dialog.setResultConverter(bt -> bt == save ? area.getText() : null);
        dialog.showAndWait().ifPresent(text -> {
            model.getConfig().setRaw(text);
            refreshJsonFromModel();
        });
    }

    private Parent buildFilesPane() {
        filesTree.setShowRoot(true);
        filesTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getFileName() == null ? item.toString() : item.getFileName().toString());
                }
            }
        });
        filesTree.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> {
            if (b == null) return;
            Path path = b.getValue();
            if (path == null || Files.isDirectory(path)) return;
            openFile(path);
        });

        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);
        codeArea.getStyleClass().add("code-area");
        VirtualizedScrollPane<CodeArea> codeScroll = new VirtualizedScrollPane<>(codeArea);

        Button openFolder = new Button("Open Folder...");
        openFolder.setOnAction(e -> chooseAndLoadFilesRoot());

        Button openFileBtn = new Button("Open File...");
        openFileBtn.setOnAction(e -> openAnyFile());

        Button newFileBtn = new Button("New File...");
        newFileBtn.getStyleClass().add("primary");
        newFileBtn.setOnAction(e -> createNewFile());

        Button saveFile = new Button("Save");
        saveFile.setOnAction(e -> saveOpenFile());
        saveFile.getStyleClass().add("primary");

        Label openFileLabel = new Label("No file open");
        openFileLabel.getStyleClass().add("header-subtitle");

        codeArea.textProperty().addListener((o, oldV, newV) -> {
            if (openFilePath != null) openFileLabel.setText(openFilePath.toString());
        });

        Label note = new Label("Tip: you can edit any file here; JSON is still the source of truth (beta).");
        note.getStyleClass().add("header-subtitle");

        HBox actions = new HBox(10, openFolder, openFileBtn, newFileBtn, saveFile, openFileLabel);
        actions.setPadding(new Insets(12));

        VBox left = new VBox(10, new Label("Files"), note, filesTree);
        left.setPadding(new Insets(12));
        VBox.setVgrow(filesTree, Priority.ALWAYS);
        left.getStyleClass().add("card");

        VBox editor = new VBox(actions, codeScroll);
        VBox.setVgrow(codeScroll, Priority.ALWAYS);
        editor.getStyleClass().add("card");

        SplitPane split = new SplitPane(left, editor);
        split.setDividerPositions(0.28);
        return split;
    }

    private Parent buildGuiPane() {
        VBox wrapper = new VBox(14);
        wrapper.setPadding(new Insets(12));

        Label title = new Label("GUI editor (beta)");
        title.getStyleClass().add("section-title");

        guiPicker.setPromptText("Select GUI...");
        guiPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GuiScreen item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle() + " (" + item.getRows() + " rows)");
            }
        });
        guiPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(GuiScreen item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getTitle());
            }
        });
        guiPicker.valueProperty().addListener((o, a, b) -> showGui(b));

        Button addGui = new Button("New GUI");
        addGui.getStyleClass().add("primary");
        addGui.setOnAction(e -> createGui());
        Button removeGui = new Button("Delete GUI");
        removeGui.setOnAction(e -> deleteGui());
        Button textures = new Button("Textures...");
        textures.setOnAction(e -> chooseTexturePack());

        HBox topRow = new HBox(10, guiPicker, addGui, removeGui, textures);
        HBox.setHgrow(guiPicker, Priority.ALWAYS);

        guiTitleField.setPromptText("Title");
        guiTitleField.textProperty().addListener((o, a, b) -> {
            GuiScreen g = guiPicker.getValue();
            if (g == null) return;
            g.setTitle(b);
            refreshGuiPicker();
            refreshJsonFromModel();
        });

        guiRowsSpinner.valueProperty().addListener((o, a, b) -> {
            GuiScreen g = guiPicker.getValue();
            if (g == null) return;
            g.setRows(b);
            rebuildGuiGrid();
            refreshGuiPicker();
            refreshJsonFromModel();
        });

        GridPane props = new GridPane();
        props.setHgap(10);
        props.setVgap(10);
        props.addRow(0, new Label("Title"), guiTitleField);
        props.addRow(1, new Label("Rows"), guiRowsSpinner);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        props.getColumnConstraints().setAll(c1, c2);

        VBox left = new VBox(12, title, topRow, props, buildGuiSlotEditor());
        left.getStyleClass().add("subcard");
        left.setPadding(new Insets(12));

        guiGrid.setHgap(8);
        guiGrid.setVgap(8);
        guiGrid.setPrefColumns(9);
        guiGrid.setTileAlignment(Pos.CENTER);
        guiGrid.getStyleClass().add("gui-grid");

        ScrollPane gridScroll = new ScrollPane(guiGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(true);
        gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.getStyleClass().add("subcard");

        SplitPane split = new SplitPane(left, gridScroll);
        split.setDividerPositions(0.38);

        wrapper.getChildren().add(split);
        VBox.setVgrow(split, Priority.ALWAYS);

        Platform.runLater(this::ensureGuiExists);
        return wrapper;
    }

    private Parent buildGuiSlotEditor() {
        Label slotTitle = new Label("Selected slot");
        slotTitle.getStyleClass().add("section-title");

        guiMaterialPicker.setEditable(true);
        guiMaterialPicker.setPromptText("STONE");
        guiMaterialPicker.setMaxWidth(Double.MAX_VALUE);
        guiMaterialPicker.getItems().setAll(listMaterials());
        guiMaterialPicker.getEditor().textProperty().addListener((o, a, b) -> filterMaterialPicker(b));
        guiMaterialPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    ImageView iv = iconView(item);
                    setGraphic(iv);
                }
            }
        });
        guiMaterialPicker.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setGraphic(null);
                } else {
                    setText(item);
                    setGraphic(iconView(item));
                }
            }
        });
        guiMaterialPicker.valueProperty().addListener((o, a, b) -> {
            if (selectedGuiSlot == null) return;
            if (b == null) return;
            selectedGuiSlot.setMaterial(b);
            refreshJsonFromModel();
            rebuildGuiGrid();
        });

        TextField name = new TextField();
        name.setPromptText("Display name");
        name.textProperty().addListener((o, a, b) -> {
            if (selectedGuiSlot == null) return;
            selectedGuiSlot.setDisplayName(b);
            refreshJsonFromModel();
            rebuildGuiGrid();
        });

        guiClickActions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CommandAction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getType().name().replace('_', ' ') + ": " + item.getText());
            }
        });

        Button add = new Button("Add click action");
        add.getStyleClass().add("primary");
        add.setOnAction(e -> addGuiClickAction());
        Button edit = new Button("Edit");
        edit.setOnAction(e -> editGuiClickAction());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> removeGuiClickAction());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Material"), guiMaterialPicker);
        grid.addRow(1, new Label("Name"), name);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        selectedGuiSlot = null;
        guiMaterialPicker.setDisable(true);
        name.setDisable(true);
        guiClickActions.setDisable(true);

        guiGrid.getChildren().addListener((javafx.collections.ListChangeListener<? super javafx.scene.Node>) c -> {
        });

        VBox box = new VBox(10, slotTitle, grid, new Label("On click"), guiClickActions, new HBox(8, add, edit, remove));
        VBox.setVgrow(guiClickActions, Priority.ALWAYS);

        guiPicker.valueProperty().addListener((o, a, b) -> {
            selectedGuiSlot = null;
            guiMaterialPicker.setDisable(true);
            name.setDisable(true);
            guiClickActions.setDisable(true);
            guiClickActions.getItems().clear();
        });

        // update controls when slot selected
        this.guiSlotSelectionListener = slot -> {
            selectedGuiSlot = slot;
            if (slot == null) {
                guiMaterialPicker.setDisable(true);
                name.setDisable(true);
                guiClickActions.setDisable(true);
                guiClickActions.getItems().clear();
            } else {
                guiMaterialPicker.setDisable(false);
                name.setDisable(false);
                guiClickActions.setDisable(false);
                guiMaterialPicker.getSelectionModel().select(slot.getMaterial());
                guiMaterialPicker.getEditor().setText(slot.getMaterial());
                name.setText(slot.getDisplayName());
                guiClickActions.getItems().setAll(slot.getOnClickActions());
            }
        };

        return box;
    }

    private interface GuiSlotSelectionListener {
        void onSelected(GuiSlot slot);
    }

    private GuiSlotSelectionListener guiSlotSelectionListener = slot -> {};

    private Parent buildJsonPane() {
        jsonEditor.setWrapText(false);

        Button apply = new Button("Apply JSON to Model");
        apply.setOnAction(e -> applyJsonToModel());

        Button refresh = new Button("Refresh JSON from Model");
        refresh.setOnAction(e -> refreshJsonFromModel());

        HBox actions = new HBox(8, apply, refresh);
        actions.setPadding(new Insets(12));

        VBox box = new VBox(actions, jsonEditor);
        VBox.setVgrow(jsonEditor, Priority.ALWAYS);
        return box;
    }

    private void refreshAllFromModel() {
        nameField.setText(model.getName());
        versionField.setText(model.getVersion());
        groupField.setText(model.getGroupId());
        mainClassField.setText(model.getMainClassName());
        refreshTree();
        refreshCommandsTable();
        refreshEventsTable();
        refreshJsonFromModel();
    }

    private void refreshTree() {
        TreeItem<String> rootItem = new TreeItem<>(model.getName());
        TreeItem<String> commands = new TreeItem<>("Commands");
        for (PluginCommand c : model.getCommands()) {
            commands.getChildren().add(new TreeItem<>(c.getName()));
        }
        TreeItem<String> events = new TreeItem<>("Events");
        for (PluginEventHandler h : model.getEvents()) {
            events.getChildren().add(new TreeItem<>(h.getMethodName()));
        }
        rootItem.getChildren().add(commands);
        rootItem.getChildren().add(events);
        rootItem.getChildren().add(new TreeItem<>("Config"));
        rootItem.setExpanded(true);
        commands.setExpanded(true);
        events.setExpanded(true);
        tree.setRoot(rootItem);
    }

    private void refreshCommandsTable() {
        commandsTable.getItems().setAll(model.getCommands());
        if (!commandsTable.getItems().isEmpty() && commandsTable.getSelectionModel().getSelectedItem() == null) {
            commandsTable.getSelectionModel().select(0);
        }
    }

    private void refreshEventsTable() {
        eventsTable.getItems().setAll(model.getEvents());
    }

    private void refreshJsonFromModel() {
        try {
            jsonEditor.setText(storage.toJson(model));
        } catch (IOException ex) {
            log("JSON serialize failed: " + ex.getMessage());
        }
    }

    private void ensureGuiExists() {
        if (model.getGuis().isEmpty()) {
            GuiScreen g = new GuiScreen();
            g.setId("main_menu");
            g.setTitle("Main Menu");
            g.setRows(3);
            model.getGuis().add(g);
        }
        refreshGuiPicker();
        if (guiPicker.getValue() == null && !model.getGuis().isEmpty()) guiPicker.setValue(model.getGuis().getFirst());
        showGui(guiPicker.getValue());
    }

    private void refreshGuiPicker() {
        guiPicker.getItems().setAll(model.getGuis());
    }

    private void showGui(GuiScreen gui) {
        if (gui == null) return;
        guiTitleField.setText(gui.getTitle());
        guiRowsSpinner.getValueFactory().setValue(gui.getRows());
        rebuildGuiGrid();
    }

    private void rebuildGuiGrid() {
        GuiScreen gui = guiPicker.getValue();
        guiGrid.getChildren().clear();
        selectedGuiSlot = null;
        guiSlotSelectionListener.onSelected(null);
        if (gui == null) return;
        int total = gui.getRows() * 9;
        for (int i = 0; i < total; i++) {
            int idx = i;
            GuiSlot slot = gui.getSlots().stream().filter(s -> s.getIndex() == idx).findFirst().orElse(null);
            Button b = new Button(slotLabel(idx, slot));
            b.setMinSize(72, 52);
            b.setMaxSize(72, 52);
            b.getStyleClass().add("slot-btn");
            if (slot != null) {
                ImageView iv = iconView(slot.getMaterial());
                if (iv != null) {
                    b.setGraphic(iv);
                    b.setContentDisplay(ContentDisplay.TOP);
                }
            }
            b.setOnAction(e -> {
                GuiSlot target = gui.getSlots().stream().filter(s -> s.getIndex() == idx).findFirst().orElse(null);
                if (target == null) {
                    GuiSlot created = new GuiSlot();
                    created.setIndex(idx);
                    created.setMaterial("STONE");
                    gui.getSlots().add(created);
                    target = created;
                }
                guiSlotSelectionListener.onSelected(target);
            });
            guiGrid.getChildren().add(b);
        }
    }

    private static String slotLabel(int idx, GuiSlot slot) {
        if (slot == null) return "#" + idx;
        String mat = slot.getMaterial() == null ? "" : slot.getMaterial();
        String name = slot.getDisplayName() == null ? "" : slot.getDisplayName();
        if (!name.isBlank()) return name;
        if (!mat.isBlank()) return mat;
        return "#" + idx;
    }

    private List<String> listMaterials() {
        try {
            return java.util.Arrays.stream(org.bukkit.Material.values())
                    .map(Enum::name)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of("STONE", "DIAMOND", "CHEST");
        }
    }

    private void filterMaterialPicker(String query) {
        if (query == null) return;
        String q = query.trim().toUpperCase();
        if (q.isEmpty()) return;
        var filtered = listMaterials().stream().filter(m -> m.contains(q)).limit(80).toList();
        guiMaterialPicker.getItems().setAll(filtered);
        if (!filtered.isEmpty() && !guiMaterialPicker.isShowing()) guiMaterialPicker.show();
    }

    private ImageView iconView(String material) {
        var img = materialIcons.findIcon(material);
        if (img == null) return null;
        ImageView iv = new ImageView(img);
        iv.setFitWidth(20);
        iv.setFitHeight(20);
        iv.setPreserveRatio(true);
        return iv;
    }

    private void chooseTexturePack() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select texture pack folder (containing assets/minecraft/textures/...)");
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected == null) return;
        materialIcons.setTexturePackRoot(selected.toPath());
        log("Texture pack set: " + selected.getAbsolutePath());
        rebuildGuiGrid();
    }

    private void createGui() {
        TextInputDialog dialog = new TextInputDialog("Main Menu");
        dialog.setHeaderText("New GUI title");
        dialog.showAndWait().ifPresent(title -> {
            GuiScreen g = new GuiScreen();
            g.setId("gui_" + (model.getGuis().size() + 1));
            g.setTitle(title.trim().isBlank() ? "GUI" : title.trim());
            g.setRows(3);
            model.getGuis().add(g);
            refreshGuiPicker();
            guiPicker.setValue(g);
            refreshJsonFromModel();
        });
    }

    private void deleteGui() {
        GuiScreen selected = guiPicker.getValue();
        if (selected == null) return;
        model.getGuis().remove(selected);
        refreshGuiPicker();
        if (!model.getGuis().isEmpty()) guiPicker.setValue(model.getGuis().getFirst());
        refreshJsonFromModel();
    }

    private void addGuiClickAction() {
        if (selectedGuiSlot == null) return;
        ChoiceDialog<CommandAction.Type> typeDialog = new ChoiceDialog<>(CommandAction.Type.SEND_MESSAGE, CommandAction.Type.values());
        typeDialog.setHeaderText("Choose click action type");
        var type = typeDialog.showAndWait();
        if (type.isEmpty()) return;

        TextInputDialog textDialog = new TextInputDialog("Clicked!");
        textDialog.setHeaderText("Action text (used by message/broadcast/run command)");
        var text = textDialog.showAndWait();
        if (text.isEmpty()) return;

        selectedGuiSlot.getOnClickActions().add(new CommandAction(type.get(), text.get()));
        guiClickActions.getItems().setAll(selectedGuiSlot.getOnClickActions());
        refreshJsonFromModel();
    }

    private void editGuiClickAction() {
        if (selectedGuiSlot == null) return;
        CommandAction selected = guiClickActions.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        ChoiceDialog<CommandAction.Type> typeDialog = new ChoiceDialog<>(selected.getType(), CommandAction.Type.values());
        typeDialog.setHeaderText("Click action type");
        var type = typeDialog.showAndWait();
        if (type.isEmpty()) return;

        TextInputDialog textDialog = new TextInputDialog(selected.getText());
        textDialog.setHeaderText("Action text");
        var text = textDialog.showAndWait();
        if (text.isEmpty()) return;

        selected.setType(type.get());
        selected.setText(text.get());
        guiClickActions.refresh();
        refreshJsonFromModel();
    }

    private void removeGuiClickAction() {
        if (selectedGuiSlot == null) return;
        CommandAction selected = guiClickActions.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        selectedGuiSlot.getOnClickActions().remove(selected);
        guiClickActions.getItems().setAll(selectedGuiSlot.getOnClickActions());
        refreshJsonFromModel();
    }

    private void applyJsonToModel() {
        try {
            PluginProject parsed = storage.fromJson(jsonEditor.getText());
            this.model = parsed;
            refreshAllFromModel();
            log("Applied JSON -> model.");
        } catch (IOException ex) {
            log("Invalid JSON: " + ex.getMessage());
        }
    }

    private void addCommand() {
        TextInputDialog nameDialog = new TextInputDialog("hello");
        nameDialog.setHeaderText("New command name");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().trim().isBlank()) return;

        TextInputDialog descDialog = new TextInputDialog("Says hello");
        descDialog.setHeaderText("Command description");
        Optional<String> desc = descDialog.showAndWait();
        if (desc.isEmpty()) return;

        model.getCommands().add(new PluginCommand(name.get().trim(), desc.get().trim()));
        refreshCommandsTable();
        refreshTree();
        refreshJsonFromModel();
    }

    private void addEvent() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("New event handler");
        dialog.setHeaderText("Add a simple event handler (beta)");
        ButtonType ok = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField eventClass = new TextField("org.bukkit.event.player.PlayerJoinEvent");
        TextField method = new TextField("onPlayerJoin");
        TextField message = new TextField("Welcome!");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Event class"), eventClass);
        grid.addRow(1, new Label("Method name"), method);
        grid.addRow(2, new Label("Message"), message);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != ok) return null;
            return new Pair<>(eventClass.getText(), method.getText() + "\n" + message.getText());
        });

        var result = dialog.showAndWait();
        if (result.isEmpty()) return;
        String cls = result.get().getKey();
        String[] parts = result.get().getValue().split("\n", 2);
        String methodName = parts.length > 0 ? parts[0] : "onEvent";
        String msg = parts.length > 1 ? parts[1] : "";
        model.getEvents().add(new PluginEventHandler(cls, methodName, msg));
        refreshEventsTable();
        refreshTree();
        refreshJsonFromModel();
    }

    private void removeEvent() {
        PluginEventHandler selected = eventsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        model.getEvents().remove(selected);
        refreshEventsTable();
        refreshTree();
        refreshJsonFromModel();
    }

    private void removeCommand() {
        PluginCommand selected = commandsTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        model.getCommands().remove(selected);
        refreshCommandsTable();
        refreshTree();
        refreshJsonFromModel();
    }

    private void newProjectWizard() {
        Dialog<PluginProject> dialog = new Dialog<>();
        dialog.setTitle("New project");
        dialog.setHeaderText("Create a new plugin project (beta)");
        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        TextField name = new TextField("MyPlugin");
        TextField version = new TextField("1.0.0");
        TextField group = new TextField("com.example");
        TextField mainClass = new TextField("Main");
        CheckBox sampleCommand = new CheckBox("Add sample /hello command");
        sampleCommand.setSelected(true);
        CheckBox sampleEvent = new CheckBox("Add sample PlayerJoinEvent handler");
        sampleEvent.setSelected(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Plugin name"), name);
        grid.addRow(1, new Label("Version"), version);
        grid.addRow(2, new Label("Group / base package"), group);
        grid.addRow(3, new Label("Main class"), mainClass);
        grid.add(sampleCommand, 1, 4);
        grid.add(sampleEvent, 1, 5);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt != create) return null;
            PluginProject p = new PluginProject();
            p.setName(name.getText());
            p.setVersion(version.getText());
            p.setGroupId(group.getText());
            p.setMainClassName(mainClass.getText());
            if (sampleCommand.isSelected()) p.getCommands().add(new PluginCommand("hello", "Says hello"));
            if (sampleEvent.isSelected())
                p.getEvents().add(new PluginEventHandler("org.bukkit.event.player.PlayerJoinEvent", "onPlayerJoin", "Welcome!"));
            return p;
        });

        Optional<PluginProject> result = dialog.showAndWait();
        if (result.isEmpty()) return;
        this.model = result.get();
        this.currentJsonPath = null;
        this.lastGenerated = null;
        this.openFilePath = null;
        codeArea.clear();
        filesTree.setRoot(null);
        refreshAllFromModel();
        log("New project created.");
    }

    private void openJson() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open project JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File selected = chooser.showOpenDialog(root.getScene().getWindow());
        if (selected == null) return;

        try {
            PluginProject loaded = storage.load(selected.toPath());
            this.model = loaded;
            this.currentJsonPath = selected.toPath();
            refreshAllFromModel();
            log("Loaded " + selected.getAbsolutePath());
        } catch (IOException ex) {
            log("Open failed: " + ex.getMessage());
        }
    }

    private void saveJson(boolean forceChooser) {
        Path target = currentJsonPath;
        if (forceChooser || target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save project JSON");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
            chooser.setInitialFileName(model.getName() + ".json");
            File selected = chooser.showSaveDialog(root.getScene().getWindow());
            if (selected == null) return;
            target = selected.toPath();
            currentJsonPath = target;
        }

        try {
            storage.save(target, model);
            log("Saved " + target.toAbsolutePath());
        } catch (IOException ex) {
            log("Save failed: " + ex.getMessage());
        }
    }

    private void generate() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select output directory");
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected == null) return;
        try {
            lastGenerated = generator.generate(model, selected.toPath());
            log("Generated project at " + lastGenerated.rootDir().toAbsolutePath());
            loadFilesRoot(lastGenerated.rootDir());
        } catch (IOException ex) {
            log("Generate failed: " + ex.getMessage());
        }
    }

    private void build() {
        if (lastGenerated == null) {
            log("Nothing generated yet. Click Generate first.");
            return;
        }

        Path dir = lastGenerated.rootDir();
        log("Running Gradle build in " + dir.toAbsolutePath() + " ...");
        background.submit(() -> {
            var result = buildService.build(dir);
            log(result.summary());
            if (!result.stdout().isBlank()) log(result.stdout().trim());
            if (!result.stderr().isBlank()) log(result.stderr().trim());
            if (result.ok()) {
                Path libs = dir.resolve("build").resolve("libs");
                if (Files.isDirectory(libs)) {
                    try {
                        List<Path> jars;
                        try (var stream = Files.list(libs)) {
                            jars = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                                    .sorted()
                                    .toList();
                        }
                        if (!jars.isEmpty()) {
                            log("Built JAR(s):");
                            for (Path jar : jars) log("  - " + jar.getFileName());
                        }
                    } catch (IOException ex) {
                        log("Couldn't list build outputs: " + ex.getMessage());
                    }
                    log("Opening output folder: " + libs.toAbsolutePath());
                    openFolder(libs);
                } else {
                    log("Build succeeded, but couldn't find " + libs.toAbsolutePath());
                }
            }
        });
    }

    private void openGeneratedRoot() {
        if (lastGenerated == null) {
            log("Nothing generated yet.");
            return;
        }
        openFolder(lastGenerated.rootDir());
    }

    private void openFolder(Path folder) {
        try {
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (Exception ex) {
            log("Open folder failed: " + ex.getMessage());
        }
    }

    private void chooseAndLoadFilesRoot() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open folder");
        File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected == null) return;
        loadFilesRoot(selected.toPath());
    }

    private void openAnyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "*.java"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yml", "*.yaml"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Gradle", "*.gradle", "*.properties"));
        File selected = chooser.showOpenDialog(root.getScene().getWindow());
        if (selected == null) return;
        openFile(selected.toPath());
    }

    private void createNewFile() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder to create file in");
        File dir = chooser.showDialog(root.getScene().getWindow());
        if (dir == null) return;

        TextInputDialog name = new TextInputDialog("NewFile.java");
        name.setHeaderText("New file name");
        var result = name.showAndWait();
        if (result.isEmpty() || result.get().trim().isBlank()) return;

        Path path = dir.toPath().resolve(result.get().trim());
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, "", StandardCharsets.UTF_8);
            }
            openFile(path);
            loadFilesRoot(dir.toPath());
        } catch (IOException ex) {
            log("Create file failed: " + ex.getMessage());
        }
    }

    private void loadFilesRoot(Path rootDir) {
        try {
            TreeItem<Path> rootItem = buildFileTree(rootDir);
            filesTree.setRoot(rootItem);
            rootItem.setExpanded(true);
        } catch (IOException ex) {
            log("Load files failed: " + ex.getMessage());
        }
    }

    private TreeItem<Path> buildFileTree(Path rootDir) throws IOException {
        TreeItem<Path> rootItem = new TreeItem<>(rootDir);
        List<Path> children = new ArrayList<>();
        try (var stream = Files.list(rootDir)) {
            stream.forEach(children::add);
        }
        children.sort(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p)).thenComparing(p -> p.getFileName().toString().toLowerCase()));
        for (Path child : children) {
            TreeItem<Path> childItem = new TreeItem<>(child);
            if (Files.isDirectory(child)) {
                childItem.getChildren().add(new TreeItem<>(child.resolve(".loading")));
                childItem.expandedProperty().addListener((o, was, is) -> {
                    if (!is) return;
                    if (childItem.getChildren().size() == 1 && childItem.getChildren().get(0).getValue().getFileName().toString().equals(".loading")) {
                        childItem.getChildren().clear();
                        try {
                            TreeItem<Path> built = buildFileTree(child);
                            childItem.getChildren().setAll(built.getChildren());
                        } catch (IOException ex) {
                            log("Folder read failed: " + ex.getMessage());
                        }
                    }
                });
            }
            rootItem.getChildren().add(childItem);
        }
        return rootItem;
    }

    private void openFile(Path path) {
        try {
            openFilePath = path;
            String text = Files.readString(path, StandardCharsets.UTF_8);
            codeArea.replaceText(text);
            log("Opened file: " + path.toAbsolutePath());
        } catch (IOException ex) {
            log("Open file failed: " + ex.getMessage());
        }
    }

    private void saveOpenFile() {
        if (openFilePath == null) {
            log("No file open.");
            return;
        }
        try {
            Files.writeString(openFilePath, codeArea.getText(), StandardCharsets.UTF_8);
            log("Saved: " + openFilePath.toAbsolutePath());
        } catch (IOException ex) {
            log("Save failed: " + ex.getMessage());
        }
    }

    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
        });
    }
}
