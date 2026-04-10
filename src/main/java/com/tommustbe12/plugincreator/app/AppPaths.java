package com.tommustbe12.plugincreator.app;

import java.nio.file.Path;

public final class AppPaths {
    private AppPaths() {}

    public static Path appDataDir() {
        String appData = System.getenv("APPDATA"); // Windows
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData).resolve("PluginCreator");
        }
        return Path.of(System.getProperty("user.home")).resolve(".plugincreator");
    }

    public static Path projectsDir() {
        return appDataDir().resolve("projects");
    }
}

