package com.tommustbe12.plugincreator;

import com.tommustbe12.plugincreator.app.AppStateStore;
import com.tommustbe12.plugincreator.app.ProjectManager;
import com.tommustbe12.plugincreator.app.ProjectWorkspace;
import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;
import com.tommustbe12.plugincreator.ui.EditorView;
import com.tommustbe12.plugincreator.ui.HomeView;
import com.tommustbe12.plugincreator.ui.LoadingView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class PluginCreatorApp extends Application {
    private final ProjectStorage storage = new ProjectStorage();
    private final AppStateStore stateStore = new AppStateStore();
    private final ProjectManager projects = new ProjectManager(storage, stateStore);

    @Override
    public void start(Stage stage) {
        LoadingView loading = new LoadingView();
        Scene scene = new Scene(loading.root(), 1100, 720);
        scene.getStylesheets().add(getClass().getResource("/com/tommustbe12/plugincreator/ui/app.css").toExternalForm());
        stage.setTitle("PluginCreator (beta)");
        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> {
            loading.setStatus("Loading home...");
            showHome(stage, scene);
        });
    }

    private void showHome(Stage stage, Scene scene) {
        var home = new HomeView(stateStore,
                () -> createNewProject(stage, scene),
                recent -> openRecent(stage, scene, recent));
        scene.setRoot(home.root());
    }

    private void createNewProject(Stage stage, Scene scene) {
        try {
            ProjectWorkspace ws = projects.createNewWorkspace("MyPlugin");
            PluginProject model = new PluginProject();
            model.getCommands().add(new PluginCommand("hello", "Says hello"));
            projects.save(ws.projectJson(), model);
            openEditor(stage, scene, ws.projectJson(), model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openRecent(Stage stage, Scene scene, String path) {
        try {
            var json = java.nio.file.Path.of(path);
            PluginProject model = projects.load(json);
            openEditor(stage, scene, json, model);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openEditor(Stage stage, Scene scene, java.nio.file.Path json, PluginProject model) {
        var editorView = new EditorView(storage, model);
        editorView.setAutosaveTarget(json);
        editorView.setOnHome(() -> Platform.runLater(() -> showHome(stage, scene)));
        scene.setRoot(editorView.root());
        stage.setOnCloseRequest(e -> editorView.shutdown());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
