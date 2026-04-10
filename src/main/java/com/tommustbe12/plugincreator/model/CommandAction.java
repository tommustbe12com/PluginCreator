package com.tommustbe12.plugincreator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CommandAction {
    public enum Type {
        SEND_MESSAGE,
        BROADCAST,
        GIVE_ITEM,
        TELEPORT_SELF,
        SET_GAMEMODE,
        RUN_CONSOLE_COMMAND
    }

    private Type type = Type.SEND_MESSAGE;
    private String text = "Hello!";
    private String material = "DIAMOND";
    private int amount = 1;
    private String world = "world";
    private double x;
    private double y;
    private double z;
    private String gamemode = "SURVIVAL";

    public CommandAction() {
    }

    public CommandAction(Type type, String text) {
        setType(type);
        setText(text);
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = Objects.requireNonNullElse(type, Type.SEND_MESSAGE);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNullElse(text, "");
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = Objects.requireNonNullElse(material, "DIAMOND").trim();
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = Objects.requireNonNullElse(world, "world").trim();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getGamemode() {
        return gamemode;
    }

    public void setGamemode(String gamemode) {
        this.gamemode = Objects.requireNonNullElse(gamemode, "SURVIVAL").trim();
    }
}
