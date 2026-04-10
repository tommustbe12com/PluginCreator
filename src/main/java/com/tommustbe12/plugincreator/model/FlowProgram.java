package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FlowProgram {
    private List<FlowBlock> blocks = new ArrayList<>();

    public List<FlowBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<FlowBlock> blocks) {
        this.blocks = (blocks == null) ? new ArrayList<>() : new ArrayList<>(blocks);
    }
}

