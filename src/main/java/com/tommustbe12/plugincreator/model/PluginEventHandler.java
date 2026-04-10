package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PluginEventHandler {
    private String eventClass = "org.bukkit.event.player.PlayerJoinEvent";
    private String methodName = "onPlayerJoin";
    private String message = "Welcome!";
    private FlowProgram program = new FlowProgram();

    public PluginEventHandler() {
    }

    public PluginEventHandler(String eventClass, String methodName, String message) {
        setEventClass(eventClass);
        setMethodName(methodName);
        setMessage(message);
        if (program.getBlocks().isEmpty()) {
            var b = new FlowBlock(FlowBlock.Type.BROADCAST);
            b.getParams().put("text", message);
            program.getBlocks().add(b);
        }
    }

    public String getEventClass() {
        return eventClass;
    }

    public void setEventClass(String eventClass) {
        this.eventClass = Objects.requireNonNullElse(eventClass, "").trim();
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = Objects.requireNonNullElse(methodName, "").trim();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = Objects.requireNonNullElse(message, "").trim();
    }

    public FlowProgram getProgram() {
        return program;
    }

    public void setProgram(FlowProgram program) {
        this.program = (program == null) ? new FlowProgram() : program;
    }
}
