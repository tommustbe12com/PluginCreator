package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PluginCommand {
    private String name = "hello";
    private String description = "Says hello";
    private java.util.List<CommandAction> actions = new java.util.ArrayList<>();

    public PluginCommand() {
    }

    public PluginCommand(String name, String description) {
        setName(name);
        setDescription(description);
        if (actions.isEmpty()) {
            actions.add(new CommandAction(CommandAction.Type.SEND_MESSAGE, this.description));
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNullElse(name, "").trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNullElse(description, "").trim();
    }

    public java.util.List<CommandAction> getActions() {
        if (actions == null) actions = new java.util.ArrayList<>();
        return actions;
    }

    public void setActions(java.util.List<CommandAction> actions) {
        this.actions = (actions == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(actions);
    }
}
