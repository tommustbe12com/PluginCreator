package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ScratchProgram {
    private List<ScratchBlock> blocks = new ArrayList<>();

    public List<ScratchBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<ScratchBlock> blocks) {
        this.blocks = (blocks == null) ? new ArrayList<>() : new ArrayList<>(blocks);
    }
}

