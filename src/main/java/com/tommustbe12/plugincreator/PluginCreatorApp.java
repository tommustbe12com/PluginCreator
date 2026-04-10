package com.tommustbe12.plugincreator;

import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;
import com.tommustbe12.plugincreator.ui.EditorView;
import com.tommustbe12.plugincreator.ui.LoadingView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class PluginCreatorApp extends Application {
    private final ProjectStorage storage = new ProjectStorage();

    @Override
    public void start(Stage stage) {
        LoadingView loading = new LoadingView();
        Scene scene = new Scene(loading.root(), 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/com/tommustbe12/plugincreator/ui/app.css").toExternalForm());
        stage.setTitle("PluginCreator (beta)");
        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> {
            loading.setStatus("Loading editor...");
            PluginProject initial = new PluginProject();
            initial.getCommands().add(new PluginCommand("hello", "Says hello"));

            var editorView = new EditorView(storage, initial);
            scene.setRoot(editorView.root());
            stage.setOnCloseRequest(e -> editorView.shutdown());
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
