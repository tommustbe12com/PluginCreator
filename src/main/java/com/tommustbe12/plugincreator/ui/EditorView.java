package com.tommustbe12.plugincreator.ui;

import com.tommustbe12.plugincreator.build.GradleBuildService;
import com.tommustbe12.plugincreator.generator.GeneratedProject;
import com.tommustbe12.plugincreator.generator.PluginGenerator;
import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginEventHandler;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Optional;

public final class EditorView {
    private final ProjectStorage storage;
    private final PluginGenerator generator = new PluginGenerator();
    private final GradleBuildService buildService = new GradleBuildService();
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

    private final TreeView<Path> filesTree = new TreeView<>();
    private final CodeArea codeArea = new CodeArea();
    private Path openFilePath;

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
        TableColumn<PluginCommand, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        nameCol.setPrefWidth(200);

        TableColumn<PluginCommand, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
        descCol.setPrefWidth(500);

        commandsTable.getColumns().addAll(nameCol, descCol);
        commandsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button add = new Button("Add");
        add.setOnAction(e -> addCommand());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> removeCommand());

        HBox actions = new HBox(8, add, remove);
        actions.setPadding(new Insets(10, 0, 0, 0));

        VBox box = new VBox(10, commandsTable, actions);
        box.setPadding(new Insets(12));
        VBox.setVgrow(commandsTable, Priority.ALWAYS);
        return box;
    }

    private Parent buildEventsPane() {
        TableColumn<PluginEventHandler, String> eventCol = new TableColumn<>("Event Class");
        eventCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEventClass()));
        eventCol.setPrefWidth(520);

        TableColumn<PluginEventHandler, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMethodName()));
        methodCol.setPrefWidth(160);

        TableColumn<PluginEventHandler, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMessage()));
        msgCol.setPrefWidth(300);

        eventsTable.getColumns().addAll(eventCol, methodCol, msgCol);
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button add = new Button("Add");
        add.setOnAction(e -> addEvent());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> removeEvent());

        HBox actions = new HBox(8, add, remove);
        actions.setPadding(new Insets(10, 0, 0, 0));

        VBox box = new VBox(10, eventsTable, actions);
        box.setPadding(new Insets(12));
        VBox.setVgrow(eventsTable, Priority.ALWAYS);
        return box;
    }

    private Parent buildConfigPane() {
        TextArea configArea = new TextArea();
        configArea.setPromptText("For now this is a simple YAML-ish text area. Future: structured editor.");
        configArea.setWrapText(false);

        Button loadFromModel = new Button("Load from Model");
        loadFromModel.setOnAction(e -> configArea.setText(configToText()));

        Button applyToModel = new Button("Apply to Model");
        applyToModel.setOnAction(e -> {
            model.getConfig().getValues().clear();
            model.getConfig().getValues().put("raw", configArea.getText());
            refreshJsonFromModel();
            log("Applied config text to model (config.values.raw).");
        });

        HBox actions = new HBox(8, loadFromModel, applyToModel);
        actions.setPadding(new Insets(12));

        VBox box = new VBox(actions, configArea);
        VBox.setVgrow(configArea, Priority.ALWAYS);
        box.setPadding(new Insets(0, 0, 12, 0));
        Platform.runLater(() -> configArea.setText(configToText()));
        return box;
    }

    private String configToText() {
        Object raw = model.getConfig().getValues().get("raw");
        if (raw instanceof String s) return s;
        return """
                # config.yml (beta)
                greeting: "Hello from config!"
                """;
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

        Button saveFile = new Button("Save");
        saveFile.setOnAction(e -> saveOpenFile());
        saveFile.getStyleClass().add("primary");

        Label openFileLabel = new Label("No file open");
        openFileLabel.getStyleClass().add("header-subtitle");

        codeArea.textProperty().addListener((o, oldV, newV) -> {
            if (openFilePath != null) openFileLabel.setText(openFilePath.toString());
        });

        HBox actions = new HBox(10, openFolder, saveFile, openFileLabel);
        actions.setPadding(new Insets(12));

        VBox left = new VBox(10, new Label("Files"), filesTree);
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
