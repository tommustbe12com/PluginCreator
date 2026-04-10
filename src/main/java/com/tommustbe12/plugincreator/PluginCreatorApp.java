package com.tommustbe12.plugincreator;

import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;
import com.tommustbe12.plugincreator.ui.EditorView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class PluginCreatorApp extends Application {
    private final ProjectStorage storage = new ProjectStorage();

    @Override
    public void start(Stage stage) {
        PluginProject initial = new PluginProject();
        initial.getCommands().add(new PluginCommand("hello", "Says hello"));

        var editorView = new EditorView(storage, initial);
        Scene scene = new Scene(editorView.root(), 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/com/tommustbe12/plugincreator/ui/app.css").toExternalForm());
        stage.setTitle("PluginCreator (beta)");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> editorView.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
