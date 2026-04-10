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
    private FlowProgram program = new FlowProgram();
    private ScratchProgram scratch = new ScratchProgram();

    public PluginCommand() {
    }

    public PluginCommand(String name, String description) {
        setName(name);
        setDescription(description);
        if (actions.isEmpty()) {
            actions.add(new CommandAction(CommandAction.Type.SEND_MESSAGE, this.description));
        }
        if (program.getBlocks().isEmpty()) {
            var b = new FlowBlock(FlowBlock.Type.SAY_TO_PLAYER);
            b.getParams().put("text", this.description);
            program.getBlocks().add(b);
        }
        if (scratch.getBlocks().isEmpty()) {
            ScratchBlock b = new ScratchBlock(ScratchBlock.Type.SAY_TEXT);
            b.getParams().put("target", "PLAYER");
            b.getParams().put("text", this.description);
            scratch.getBlocks().add(b);
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

    public FlowProgram getProgram() {
        return program;
    }

    public void setProgram(FlowProgram program) {
        this.program = (program == null) ? new FlowProgram() : program;
    }

    public ScratchProgram getScratch() {
        return scratch;
    }

    public void setScratch(ScratchProgram scratch) {
        this.scratch = (scratch == null) ? new ScratchProgram() : scratch;
    }
}
