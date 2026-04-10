package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuiSlot {
    private int index; // 0..(rows*9-1)
    private String material = "STONE";
    private String displayName = "";
    private List<String> lore = new ArrayList<>();
    private List<CommandAction> onClickActions = new ArrayList<>();

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = Math.max(0, index);
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = Objects.requireNonNullElse(material, "STONE").trim();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = Objects.requireNonNullElse(displayName, "");
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = (lore == null) ? new ArrayList<>() : new ArrayList<>(lore);
    }

    public List<CommandAction> getOnClickActions() {
        return onClickActions;
    }

    public void setOnClickActions(List<CommandAction> onClickActions) {
        this.onClickActions = (onClickActions == null) ? new ArrayList<>() : new ArrayList<>(onClickActions);
    }
}

