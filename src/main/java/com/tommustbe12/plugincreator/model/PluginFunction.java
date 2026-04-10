package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PluginFunction {
    private String name = "myFunction";
    private FlowProgram program = new FlowProgram();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNullElse(name, "").trim();
    }

    public FlowProgram getProgram() {
        return program;
    }

    public void setProgram(FlowProgram program) {
        this.program = (program == null) ? new FlowProgram() : program;
    }
}

