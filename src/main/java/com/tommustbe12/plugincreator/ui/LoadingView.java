package com.tommustbe12.plugincreator.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class LoadingView {
    private final StackPane root = new StackPane();
    private final Label status = new Label("Starting…");

    public LoadingView() {
        root.getStyleClass().add("app-shell");

        Label title = new Label("PluginCreator");
        title.getStyleClass().add("loading-title");
        Label subtitle = new Label("For PluginCreator by TomMustBe12");
        subtitle.getStyleClass().add("loading-subtitle");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(54, 54);

        status.getStyleClass().add("loading-status");

        VBox box = new VBox(10, title, subtitle, spinner, status);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("loading-card");

        root.getChildren().add(box);
    }

    public Parent root() {
        return root;
    }

    public void setStatus(String text) {
        status.setText(text);
    }
}

