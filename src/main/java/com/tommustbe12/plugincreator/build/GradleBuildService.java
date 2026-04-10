package com.tommustbe12.plugincreator.build;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class GradleBuildService {
    public BuildResult build(Path projectDir) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(projectDir.toFile());
        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher launcher = connection.newBuild()
                    .forTasks("clean", "build")
                    .setStandardOutput(out)
                    .setStandardError(err);

            launcher.run();
            return new BuildResult(true, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), null);
        } catch (Exception ex) {
            return new BuildResult(false, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), ex);
        }
    }

    public record BuildResult(boolean ok, String stdout, String stderr, Exception error) {
        public String summary() {
            if (ok) return "Gradle build succeeded.";
            if (error == null) return "Gradle build failed.";
            return "Gradle build failed: " + error.getMessage();
        }
    }
}

