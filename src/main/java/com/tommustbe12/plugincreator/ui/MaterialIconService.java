package com.tommustbe12.plugincreator.ui;

import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MaterialIconService {
    private final Map<String, Image> cache = new ConcurrentHashMap<>();
    private volatile Path texturePackRoot;

    public void setTexturePackRoot(Path root) {
        this.texturePackRoot = root;
        cache.clear();
    }

    public Path getTexturePackRoot() {
        return texturePackRoot;
    }

    public Image findIcon(String materialName) {
        if (materialName == null || materialName.isBlank()) return null;
        Path root = texturePackRoot;
        if (root == null) return null;

        String key = materialName.trim().toUpperCase(Locale.ROOT);
        return cache.computeIfAbsent(key, k -> load(k, root));
    }

    private Image load(String materialName, Path root) {
        String fileBase = materialName.toLowerCase(Locale.ROOT);
        // best-effort: support common resource pack layouts
        Path[] candidates = new Path[] {
                root.resolve("assets/minecraft/textures/item/" + fileBase + ".png"),
                root.resolve("assets/minecraft/textures/block/" + fileBase + ".png"),
                root.resolve("textures/item/" + fileBase + ".png"),
                root.resolve("textures/block/" + fileBase + ".png")
        };

        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            try (InputStream in = Files.newInputStream(p)) {
                return new Image(in);
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}

