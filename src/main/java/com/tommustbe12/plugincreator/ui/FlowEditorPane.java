package com.tommustbe12.plugincreator.ui;

import com.tommustbe12.plugincreator.model.FlowBlock;
import com.tommustbe12.plugincreator.model.FlowProgram;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * "Scratch-ish" flow editor MVP: palette -> big workspace -> per-block edit.
 * Drag/drop is future; this version focuses on clarity and speed.
 */
public final class FlowEditorPane {
    private final VBox root = new VBox(12);

    private final ListView<FlowBlock> workspace = new ListView<>();
    private final ComboBox<FlowBlock.Type> palette = new ComboBox<>();
    private final Button addBlockBtn = new Button("Add block");
    private final Button deleteBtn = new Button("Delete");
    private final Button upBtn = new Button("Up");
    private final Button downBtn = new Button("Down");

    private final TextArea paramsEditor = new TextArea();

    private FlowProgram program = new FlowProgram();
    private Runnable onChange = () -> {};

    public FlowEditorPane() {
        root.getStyleClass().add("flow-editor");
        root.setPadding(new Insets(12));

        Label title = new Label("Blocks");
        title.getStyleClass().add("section-title");

        palette.getItems().setAll(FlowBlock.Type.values());
        palette.getSelectionModel().select(FlowBlock.Type.SAY_TO_PLAYER);
        palette.setMaxWidth(Double.MAX_VALUE);

        addBlockBtn.getStyleClass().add("primary");
        addBlockBtn.setOnAction(e -> addBlock());

        HBox paletteRow = new HBox(10, palette, addBlockBtn);
        HBox.setHgrow(palette, Priority.ALWAYS);

        workspace.setPrefHeight(420);
        workspace.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FlowBlock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(pretty(item));
            }
        });
        workspace.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showBlock(b));

        upBtn.setOnAction(e -> move(-1));
        downBtn.setOnAction(e -> move(1));
        deleteBtn.setOnAction(e -> deleteSelected());

        HBox editRow = new HBox(10, upBtn, downBtn, deleteBtn);

        Label propsTitle = new Label("Block properties");
        propsTitle.getStyleClass().add("section-title");
        paramsEditor.setPromptText("Key=Value per line (beta)\nExamples:\ntext=Hello!\ngamemode=CREATIVE\nmaterial=DIAMOND\namount=1\nguiId=main_menu\nworld=world\nx=0\ny=80\nz=0");
        paramsEditor.textProperty().addListener((o, a, b) -> applyParams());
        paramsEditor.setPrefRowCount(8);

        root.getChildren().addAll(title, paletteRow, workspace, editRow, propsTitle, paramsEditor);
        VBox.setVgrow(workspace, Priority.ALWAYS);
    }

    public Parent root() {
        return root;
    }

    public void setProgram(FlowProgram program) {
        this.program = Objects.requireNonNullElse(program, new FlowProgram());
        workspace.getItems().setAll(this.program.getBlocks());
        if (!workspace.getItems().isEmpty()) workspace.getSelectionModel().select(0);
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = (onChange == null) ? () -> {} : onChange;
    }

    private void addBlock() {
        FlowBlock.Type type = palette.getSelectionModel().getSelectedItem();
        if (type == null) type = FlowBlock.Type.SAY_TO_PLAYER;
        FlowBlock block = new FlowBlock(type);
        seedDefaults(block);
        program.getBlocks().add(block);
        workspace.getItems().setAll(program.getBlocks());
        workspace.getSelectionModel().select(block);
        onChange.run();
    }

    private void deleteSelected() {
        FlowBlock selected = workspace.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        program.getBlocks().remove(selected);
        workspace.getItems().setAll(program.getBlocks());
        onChange.run();
    }

    private void move(int delta) {
        int idx = workspace.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        int next = idx + delta;
        if (next < 0 || next >= program.getBlocks().size()) return;
        FlowBlock a = program.getBlocks().get(idx);
        program.getBlocks().set(idx, program.getBlocks().get(next));
        program.getBlocks().set(next, a);
        workspace.getItems().setAll(program.getBlocks());
        workspace.getSelectionModel().select(next);
        onChange.run();
    }

    private void showBlock(FlowBlock block) {
        if (block == null) {
            paramsEditor.setText("");
            paramsEditor.setDisable(true);
            return;
        }
        paramsEditor.setDisable(false);
        paramsEditor.setText(block.getParams().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(""));
    }

    private void applyParams() {
        FlowBlock selected = workspace.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        selected.getParams().clear();
        for (String line : paramsEditor.getText().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String k = trimmed.substring(0, eq).trim();
            String v = trimmed.substring(eq + 1).trim();
            selected.getParams().put(k, v);
        }
        workspace.refresh();
        onChange.run();
    }

    private static String pretty(FlowBlock block) {
        String text = block.getParams().getOrDefault("text", "");
        return switch (block.getType()) {
            case SAY_TO_PLAYER -> "Say to player: " + shortText(text);
            case BROADCAST -> "Broadcast: " + shortText(text);
            case SET_GAMEMODE -> "Set gamemode: " + block.getParams().getOrDefault("gamemode", "SURVIVAL");
            case GIVE_ITEM -> "Give item: " + block.getParams().getOrDefault("material", "DIAMOND") + " x" + block.getParams().getOrDefault("amount", "1");
            case TELEPORT_PLAYER -> "Teleport: " + block.getParams().getOrDefault("world", "world") + " (" +
                    block.getParams().getOrDefault("x", "0") + "," +
                    block.getParams().getOrDefault("y", "0") + "," +
                    block.getParams().getOrDefault("z", "0") + ")";
            case OPEN_GUI -> "Open GUI: " + block.getParams().getOrDefault("guiId", "main_menu");
            case RUN_CONSOLE_COMMAND -> "Run console: " + shortText(block.getParams().getOrDefault("command", text));
        };
    }

    private static String shortText(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() > 40 ? t.substring(0, 37) + "..." : t;
    }

    private static void seedDefaults(FlowBlock block) {
        switch (block.getType()) {
            case SAY_TO_PLAYER, BROADCAST -> block.getParams().putIfAbsent("text", "Hello!");
            case SET_GAMEMODE -> block.getParams().putIfAbsent("gamemode", "SURVIVAL");
            case GIVE_ITEM -> {
                block.getParams().putIfAbsent("material", "DIAMOND");
                block.getParams().putIfAbsent("amount", "1");
            }
            case TELEPORT_PLAYER -> {
                block.getParams().putIfAbsent("world", "world");
                block.getParams().putIfAbsent("x", "0");
                block.getParams().putIfAbsent("y", "80");
                block.getParams().putIfAbsent("z", "0");
            }
            case OPEN_GUI -> block.getParams().putIfAbsent("guiId", "main_menu");
            case RUN_CONSOLE_COMMAND -> block.getParams().putIfAbsent("command", "say Hello from console");
        }
    }
}

