package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PluginProject {
    private String name = "MyPlugin";
    private String version = "1.0.0";
    private String groupId = "com.example";
    private String mainClassName = "Main";
    private List<PluginCommand> commands = new ArrayList<>();
    private List<PluginEventHandler> events = new ArrayList<>();
    private MapConfig config = new MapConfig();
    private List<GuiScreen> guis = new ArrayList<>();
    private List<PluginFunction> functions = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNullElse(name, "").trim();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = Objects.requireNonNullElse(version, "").trim();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = Objects.requireNonNullElse(groupId, "").trim();
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = Objects.requireNonNullElse(mainClassName, "").trim();
    }

    public List<PluginCommand> getCommands() {
        return commands;
    }

    public void setCommands(List<PluginCommand> commands) {
        this.commands = (commands == null) ? new ArrayList<>() : new ArrayList<>(commands);
    }

    public List<PluginEventHandler> getEvents() {
        return events;
    }

    public void setEvents(List<PluginEventHandler> events) {
        this.events = (events == null) ? new ArrayList<>() : new ArrayList<>(events);
    }

    public MapConfig getConfig() {
        return config;
    }

    public void setConfig(MapConfig config) {
        this.config = (config == null) ? new MapConfig() : config;
    }

    public List<GuiScreen> getGuis() {
        return guis;
    }

    public void setGuis(List<GuiScreen> guis) {
        this.guis = (guis == null) ? new ArrayList<>() : new ArrayList<>(guis);
    }

    public List<PluginFunction> getFunctions() {
        return functions;
    }

    public void setFunctions(List<PluginFunction> functions) {
        this.functions = (functions == null) ? new ArrayList<>() : new ArrayList<>(functions);
    }
}
