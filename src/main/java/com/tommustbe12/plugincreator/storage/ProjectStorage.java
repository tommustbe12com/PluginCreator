package com.tommustbe12.plugincreator.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tommustbe12.plugincreator.model.PluginProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectStorage {
    private final ObjectMapper objectMapper;

    public ProjectStorage() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public PluginProject load(Path jsonPath) throws IOException {
        try (var in = Files.newBufferedReader(jsonPath)) {
            return objectMapper.readValue(in, PluginProject.class);
        }
    }

    public void save(Path jsonPath, PluginProject project) throws IOException {
        Files.createDirectories(jsonPath.getParent());
        try (var out = Files.newBufferedWriter(jsonPath)) {
            objectMapper.writeValue(out, project);
        }
    }

    public String toJson(PluginProject project) throws IOException {
        return objectMapper.writeValueAsString(project);
    }

    public PluginProject fromJson(String json) throws IOException {
        return objectMapper.readValue(json, PluginProject.class);
    }
}

