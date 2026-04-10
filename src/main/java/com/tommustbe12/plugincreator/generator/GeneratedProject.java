package com.tommustbe12.plugincreator.generator;

import com.tommustbe12.plugincreator.model.PluginProject;

import java.nio.file.Path;

public record GeneratedProject(PluginProject model, Path rootDir) {
}

