package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class GuiScreen {
    private String id = "main_menu";
    private String title = "Main Menu";
    private int rows = 3; // 1..6
    private List<GuiSlot> slots = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = Objects.requireNonNullElse(id, "").trim();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNullElse(title, "").trim();
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.min(6, Math.max(1, rows));
    }

    public List<GuiSlot> getSlots() {
        return slots;
    }

    public void setSlots(List<GuiSlot> slots) {
        this.slots = (slots == null) ? new ArrayList<>() : new ArrayList<>(slots);
    }
}

