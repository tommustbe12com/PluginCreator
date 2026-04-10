package com.tommustbe12.plugincreator.ui;

import com.tommustbe12.plugincreator.model.ScratchBlock;
import com.tommustbe12.plugincreator.model.ScratchProgram;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.util.*;

/**
 * Scratch-like block editor MVP with real drag+drop and C-block nesting.
 * Drag from palette into workspace, and drag blocks to reorder/nest.
 */
public final class ScratchBlockEditor {
    private static final String DB_KIND = "kind"; // palette|existing
    private static final String DB_TYPE = "type";
    private static final String DB_ID = "id";

    private final BorderPane root = new BorderPane();
    private final VBox palette = new VBox(10);
    private final VBox workspace = new VBox(8);
    private final ScrollPane workspaceScroll = new ScrollPane(workspace);

    private ScratchProgram program = new ScratchProgram();
    private Runnable onChange = () -> {};
    private final IdentityHashMap<ScratchBlock, String> ids = new IdentityHashMap<>();

    public ScratchBlockEditor() {
        root.getStyleClass().add("scratch-editor");

        palette.setPadding(new Insets(12));
        palette.getStyleClass().add("scratch-palette");
        palette.getChildren().addAll(
                section("Looks"),
                paletteBlock(ScratchBlock.Type.SAY_TEXT, "Say text"),
                section("Player"),
                paletteBlock(ScratchBlock.Type.SET_GAMEMODE, "Set gamemode"),
                paletteBlock(ScratchBlock.Type.OPEN_GUI, "Open GUI"),
                section("Control"),
                paletteBlock(ScratchBlock.Type.REPEAT_C, "Repeat"),
                paletteBlock(ScratchBlock.Type.IF_C, "If"),
                section("System"),
                paletteBlock(ScratchBlock.Type.RUN_CONSOLE_COMMAND, "Run console command")
        );

        workspace.setPadding(new Insets(14));
        workspace.getStyleClass().add("scratch-workspace");
        workspaceScroll.setFitToWidth(true);
        workspaceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        workspaceScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        root.setLeft(palette);
        root.setCenter(workspaceScroll);

        setDropTarget(workspace, () -> insertInto(program.getBlocks(), program.getBlocks().size(), null));
    }

    public Parent root() {
        return root;
    }

    public void setProgram(ScratchProgram program) {
        this.program = Objects.requireNonNullElse(program, new ScratchProgram());
        render();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = (onChange == null) ? () -> {} : onChange;
    }

    private Label section(String name) {
        Label l = new Label(name);
        l.getStyleClass().add("scratch-section");
        return l;
    }

    private Parent paletteBlock(ScratchBlock.Type type, String label) {
        Label block = new Label(label);
        block.getStyleClass().addAll("scratch-block", "palette-block");
        block.setMaxWidth(Double.MAX_VALUE);
        block.setPadding(new Insets(10, 12, 10, 12));
        block.setOnDragDetected(e -> {
            Dragboard db = block.startDragAndDrop(TransferMode.COPY);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(DB_KIND + "=palette;" + DB_TYPE + "=" + type.name() + ";");
            db.setContent(cc);
            e.consume();
        });
        return block;
    }

    private void render() {
        workspace.getChildren().clear();
        for (int i = 0; i < program.getBlocks().size(); i++) {
            ScratchBlock b = program.getBlocks().get(i);
            workspace.getChildren().add(blockNode(program.getBlocks(), i, b));
        }
        if (program.getBlocks().isEmpty()) {
            Label hint = new Label("Drag blocks here");
            hint.getStyleClass().add("scratch-hint");
            workspace.getChildren().add(hint);
        }
    }

    private Parent blockNode(List<ScratchBlock> list, int index, ScratchBlock block) {
        String id = ids.computeIfAbsent(block, b -> UUID.randomUUID().toString());

        if (block.getType() == ScratchBlock.Type.REPEAT_C || block.getType() == ScratchBlock.Type.IF_C) {
            return cBlockNode(list, index, block, id);
        }

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("scratch-block");
        row.getStyleClass().add("stack-block");

        Label title = new Label(prettyType(block.getType()));
        title.getStyleClass().add("scratch-block-title");

        Parent editor = inlineEditor(block);
        HBox.setHgrow(editor, Priority.ALWAYS);

        Button del = new Button("×");
        del.getStyleClass().add("scratch-del");
        del.setOnAction(e -> {
            list.remove(block);
            onChange.run();
            render();
        });

        row.getChildren().addAll(title, editor, del);
        setDragSource(row, block, id);
        setDropTarget(row, () -> insertInto(list, index, null));
        return row;
    }

    private Parent cBlockNode(List<ScratchBlock> list, int index, ScratchBlock block, String id) {
        VBox outer = new VBox(8);
        outer.getStyleClass().addAll("scratch-block", "c-block");
        outer.setPadding(new Insets(10, 12, 10, 12));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(prettyType(block.getType()));
        title.getStyleClass().add("scratch-block-title");

        Parent editor = inlineEditor(block);
        HBox.setHgrow(editor, Priority.ALWAYS);

        Button del = new Button("×");
        del.getStyleClass().add("scratch-del");
        del.setOnAction(e -> {
            list.remove(block);
            onChange.run();
            render();
        });

        header.getChildren().addAll(title, editor, del);
        setDragSource(header, block, id);
        setDropTarget(header, () -> insertInto(list, index, null));

        VBox inner = new VBox(8);
        inner.getStyleClass().add("c-inner");
        inner.setPadding(new Insets(10, 0, 0, 18));

        setDropTarget(inner, () -> insertInto(block.getChildren(), block.getChildren().size(), block));

        for (int i = 0; i < block.getChildren().size(); i++) {
            ScratchBlock child = block.getChildren().get(i);
            inner.getChildren().add(blockNode(block.getChildren(), i, child));
        }
        if (block.getChildren().isEmpty()) {
            Label hint = new Label("Drop blocks inside");
            hint.getStyleClass().add("scratch-hint");
            inner.getChildren().add(hint);
        }

        outer.getChildren().addAll(header, inner);
        return outer;
    }

    private Parent inlineEditor(ScratchBlock block) {
        return switch (block.getType()) {
            case SAY_TEXT -> {
                ComboBox<String> target = new ComboBox<>();
                target.getItems().setAll("PLAYER", "ALL_PLAYERS");
                target.getSelectionModel().select(block.getParams().getOrDefault("target", "PLAYER"));
                target.valueProperty().addListener((o, a, b) -> {
                    block.getParams().put("target", b);
                    onChange.run();
                });

                TextField text = new TextField(block.getParams().getOrDefault("text", "Hello!"));
                text.setPromptText("text");
                text.textProperty().addListener((o, a, b) -> {
                    block.getParams().put("text", b);
                    onChange.run();
                });

                HBox box = new HBox(10, new Label("to"), target, text);
                box.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(text, Priority.ALWAYS);
                yield box;
            }
            case SET_GAMEMODE -> {
                ComboBox<String> gm = new ComboBox<>();
                gm.getItems().setAll("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR");
                gm.getSelectionModel().select(block.getParams().getOrDefault("gamemode", "SURVIVAL"));
                gm.valueProperty().addListener((o, a, b) -> {
                    block.getParams().put("gamemode", b);
                    onChange.run();
                });
                yield new HBox(10, new Label("→"), gm);
            }
            case OPEN_GUI -> {
                TextField id = new TextField(block.getParams().getOrDefault("guiId", "main_menu"));
                id.setPromptText("guiId");
                id.textProperty().addListener((o, a, b) -> {
                    block.getParams().put("guiId", b);
                    onChange.run();
                });
                yield new HBox(10, new Label("id"), id);
            }
            case RUN_CONSOLE_COMMAND -> {
                TextField cmd = new TextField(block.getParams().getOrDefault("command", "say Hello"));
                cmd.setPromptText("command");
                cmd.textProperty().addListener((o, a, b) -> {
                    block.getParams().put("command", b);
                    onChange.run();
                });
                yield cmd;
            }
            case REPEAT_C -> {
                Spinner<Integer> count = new Spinner<>(1, 999, Integer.parseInt(block.getParams().getOrDefault("count", "5")));
                count.valueProperty().addListener((o, a, b) -> {
                    block.getParams().put("count", String.valueOf(b));
                    onChange.run();
                });
                yield new HBox(10, new Label("times"), count);
            }
            case IF_C -> {
                ComboBox<String> cond = new ComboBox<>();
                cond.getItems().setAll("PLAYER_PRESENT", "HAS_PERMISSION");
                cond.getSelectionModel().select(block.getParams().getOrDefault("cond", "PLAYER_PRESENT"));
                TextField arg = new TextField(block.getParams().getOrDefault("perm", "plugin.use"));
                arg.setPromptText("permission");
                arg.setVisible("HAS_PERMISSION".equals(cond.getValue()));
                arg.managedProperty().bind(arg.visibleProperty());
                cond.valueProperty().addListener((o, a, b) -> {
                    block.getParams().put("cond", b);
                    arg.setVisible("HAS_PERMISSION".equals(b));
                    onChange.run();
                });
                arg.textProperty().addListener((o, a, b) -> {
                    block.getParams().put("perm", b);
                    onChange.run();
                });
                yield new HBox(10, cond, arg);
            }
        };
    }

    private void setDragSource(Region node, ScratchBlock block, String id) {
        node.setOnDragDetected(e -> {
            Dragboard db = node.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString(DB_KIND + "=existing;" + DB_ID + "=" + id + ";");
            db.setContent(cc);
            e.consume();
        });
    }

    private void setDropTarget(Region node, Runnable onDropHere) {
        node.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        node.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (!db.hasString()) return;
            String s = db.getString();
            if (s.contains(DB_KIND + "=palette")) {
                ScratchBlock.Type type = ScratchBlock.Type.valueOf(extract(s, DB_TYPE));
                ScratchBlock b = new ScratchBlock(type);
                seedDefaults(b);
                insertNew(b, onDropHere);
                e.setDropCompleted(true);
                e.consume();
                return;
            }
            if (s.contains(DB_KIND + "=existing")) {
                String id = extract(s, DB_ID);
                ScratchBlock moving = findById(program.getBlocks(), id);
                if (moving != null) {
                    removeById(program.getBlocks(), id);
                    insertExisting(moving, onDropHere);
                    e.setDropCompleted(true);
                    e.consume();
                    return;
                }
            }
            e.setDropCompleted(false);
            e.consume();
        });
    }

    private void insertInto(List<ScratchBlock> list, int index, ScratchBlock parentC) {
        // used only as closure to mark insertion position; real insertion happens by the caller methods
        // We implement insertion by storing a threadlocal insertion request during drop.
        Insertion.set(new Insertion(list, index));
    }

    private void insertNew(ScratchBlock b, Insertion ins) {
        ins.list.add(Math.min(ins.index, ins.list.size()), b);
    }

    private void insertExisting(ScratchBlock b, Insertion ins) {
        ins.list.add(Math.min(ins.index, ins.list.size()), b);
    }

    private void insertNew(ScratchBlock b, Runnable onDropHere, boolean isNew) {
        Insertion.clear();
        onDropHere.run();
        Insertion ins = Insertion.get();
        if (ins == null) {
            program.getBlocks().add(b);
        } else {
            insertNew(b, ins);
        }
        Insertion.clear();
        onChange.run();
        render();
    }

    private void insertExisting(ScratchBlock b, Runnable onDropHere, boolean ignored) {
        Insertion.clear();
        onDropHere.run();
        Insertion ins = Insertion.get();
        if (ins == null) {
            program.getBlocks().add(b);
        } else {
            insertExisting(b, ins);
        }
        Insertion.clear();
        onChange.run();
        render();
    }

    private void insertNew(ScratchBlock b, Runnable onDropHere) {
        insertNew(b, onDropHere, true);
    }

    private void insertExisting(ScratchBlock b, Runnable onDropHere) {
        insertExisting(b, onDropHere, false);
    }

    private static String extract(String s, String key) {
        int i = s.indexOf(key + "=");
        if (i < 0) return "";
        int start = i + key.length() + 1;
        int end = s.indexOf(';', start);
        if (end < 0) end = s.length();
        return s.substring(start, end);
    }

    private ScratchBlock findById(List<ScratchBlock> list, String id) {
        for (ScratchBlock b : list) {
            String bid = ids.computeIfAbsent(b, x -> UUID.randomUUID().toString());
            if (Objects.equals(bid, id)) return b;
            ScratchBlock in = findById(b.getChildren(), id);
            if (in != null) return in;
        }
        return null;
    }

    private boolean removeById(List<ScratchBlock> list, String id) {
        Iterator<ScratchBlock> it = list.iterator();
        while (it.hasNext()) {
            ScratchBlock b = it.next();
            String bid = ids.computeIfAbsent(b, x -> UUID.randomUUID().toString());
            if (Objects.equals(bid, id)) {
                it.remove();
                return true;
            }
            if (removeById(b.getChildren(), id)) return true;
        }
        return false;
    }

    private static boolean contains(List<ScratchBlock> list, ScratchBlock target) {
        for (ScratchBlock b : list) {
            if (b == target) return true;
            if (contains(b.getChildren(), target)) return true;
        }
        return false;
    }

    private static String prettyType(ScratchBlock.Type type) {
        return switch (type) {
            case SAY_TEXT -> "say";
            case SET_GAMEMODE -> "set gamemode";
            case OPEN_GUI -> "open gui";
            case RUN_CONSOLE_COMMAND -> "run console";
            case REPEAT_C -> "repeat";
            case IF_C -> "if";
        };
    }

    private static void seedDefaults(ScratchBlock b) {
        switch (b.getType()) {
            case SAY_TEXT -> {
                b.getParams().putIfAbsent("target", "PLAYER");
                b.getParams().putIfAbsent("text", "Hello!");
            }
            case SET_GAMEMODE -> b.getParams().putIfAbsent("gamemode", "SURVIVAL");
            case OPEN_GUI -> b.getParams().putIfAbsent("guiId", "main_menu");
            case RUN_CONSOLE_COMMAND -> b.getParams().putIfAbsent("command", "say Hello");
            case REPEAT_C -> b.getParams().putIfAbsent("count", "5");
            case IF_C -> {
                b.getParams().putIfAbsent("cond", "PLAYER_PRESENT");
                b.getParams().putIfAbsent("perm", "plugin.use");
            }
        }
    }

    private record Insertion(List<ScratchBlock> list, int index) {
        private static final ThreadLocal<Insertion> TL = new ThreadLocal<>();
        static void set(Insertion ins) { TL.set(ins); }
        static Insertion get() { return TL.get(); }
        static void clear() { TL.remove(); }
    }
}
