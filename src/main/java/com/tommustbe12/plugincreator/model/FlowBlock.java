package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FlowBlock {
    public enum Type {
        SAY_TO_PLAYER,
        BROADCAST,
        SET_GAMEMODE,
        GIVE_ITEM,
        TELEPORT_PLAYER,
        OPEN_GUI,
        RUN_CONSOLE_COMMAND
    }

    private Type type = Type.SAY_TO_PLAYER;
    private Map<String, String> params = new LinkedHashMap<>();

    public FlowBlock() {
    }

    public FlowBlock(Type type) {
        setType(type);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = Objects.requireNonNullElse(type, Type.SAY_TO_PLAYER);
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = (params == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }
}

