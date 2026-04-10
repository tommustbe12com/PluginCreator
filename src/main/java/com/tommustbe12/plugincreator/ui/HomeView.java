package com.tommustbe12.plugincreator.ui;

import com.tommustbe12.plugincreator.app.AppStateStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class HomeView {
    private final VBox root = new VBox(18);

    public HomeView(AppStateStore state, Runnable onCreate, Consumer<String> onOpenRecent) {
        root.getStyleClass().add("home");
        root.setPadding(new Insets(22));

        Label title = new Label("Welcome");
        title.getStyleClass().add("home-title");
        Label subtitle = new Label("Create your first plugin, or pick up where you left off.");
        subtitle.getStyleClass().add("home-subtitle");

        Button create = new Button("Create plugin");
        create.getStyleClass().addAll("primary", "home-cta");
        create.setOnAction(e -> onCreate.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox hero = new HBox(12, new VBox(6, title, subtitle), spacer, create);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("home-hero");
        hero.setPadding(new Insets(18));

        Label recentsLabel = new Label("Recent projects");
        recentsLabel.getStyleClass().add("section-title");

        ListView<String> recents = new ListView<>();
        recents.getStyleClass().add("home-recents");
        recents.getItems().setAll(state.recentProjects());
        recents.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        recents.setOnMouseClicked(e -> {
            String selected = recents.getSelectionModel().getSelectedItem();
            if (selected != null) onOpenRecent.accept(selected);
        });

        VBox card = new VBox(12, recentsLabel, recents);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        VBox.setVgrow(recents, Priority.ALWAYS);

        root.getChildren().addAll(hero, card);
        VBox.setVgrow(card, Priority.ALWAYS);
    }

    public Parent root() {
        return root;
    }
}

