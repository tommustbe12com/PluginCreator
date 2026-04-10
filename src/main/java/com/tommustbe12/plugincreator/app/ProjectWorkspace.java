package com.tommustbe12.plugincreator.app;

import java.nio.file.Path;

public record ProjectWorkspace(Path projectJson, Path outputDir) {
}

