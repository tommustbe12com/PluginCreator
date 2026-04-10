package com.tommustbe12.plugincreator.app;

import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.storage.ProjectStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class ProjectManager {
    private final ProjectStorage storage;
    private final AppStateStore stateStore;

    public ProjectManager(ProjectStorage storage, AppStateStore stateStore) {
        this.storage = storage;
        this.stateStore = stateStore;
    }

    public ProjectWorkspace createNewWorkspace(String suggestedName) throws IOException {
        Files.createDirectories(AppPaths.projectsDir());
        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        String safe = (suggestedName == null || suggestedName.isBlank()) ? "MyPlugin" : suggestedName.replaceAll("[^A-Za-z0-9._-]", "");
        Path json = AppPaths.projectsDir().resolve(safe + "-" + id + ".json");
        return new ProjectWorkspace(json, null);
    }

    public PluginProject load(Path json) throws IOException {
        stateStore.pushRecent(json.toAbsolutePath().toString());
        return storage.load(json);
    }

    public void save(Path json, PluginProject model) throws IOException {
        storage.save(json, model);
        stateStore.pushRecent(json.toAbsolutePath().toString());
    }
}

