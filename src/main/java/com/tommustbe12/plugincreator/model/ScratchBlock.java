package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ScratchBlock {
    public enum Type {
        SAY_TEXT,
        SET_GAMEMODE,
        OPEN_GUI,
        RUN_CONSOLE_COMMAND,
        REPEAT_C,
        IF_C
    }

    private Type type = Type.SAY_TEXT;
    private Map<String, String> params = new LinkedHashMap<>();
    private List<ScratchBlock> children = new ArrayList<>();

    public ScratchBlock() {
    }

    public ScratchBlock(Type type) {
        setType(type);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = Objects.requireNonNullElse(type, Type.SAY_TEXT);
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = (params == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }

    public List<ScratchBlock> getChildren() {
        return children;
    }

    public void setChildren(List<ScratchBlock> children) {
        this.children = (children == null) ? new ArrayList<>() : new ArrayList<>(children);
    }
}

