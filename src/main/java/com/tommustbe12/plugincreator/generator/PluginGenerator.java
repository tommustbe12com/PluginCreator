package com.tommustbe12.plugincreator.generator;

import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginEventHandler;
import com.tommustbe12.plugincreator.model.PluginProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PluginGenerator {
    private final TemplateEngine templateEngine = new TemplateEngine();

    public GeneratedProject generate(PluginProject project, Path outputDir) throws IOException {
        String safeName = sanitize(project.getName());
        Path root = outputDir.resolve(safeName);
        Files.createDirectories(root);

        writeText(root.resolve("settings.gradle"), templateSettingsGradle(safeName));
        writeText(root.resolve("build.gradle"), templateBuildGradle(project));
        writeText(root.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx1g\n");

        Path resources = root.resolve("src/main/resources");
        Files.createDirectories(resources);
        writeText(resources.resolve("plugin.yml"), templatePluginYml(project));
        writeText(resources.resolve("config.yml"), templateConfigYml(project));

        String pkg = project.getGroupId() + "." + safeName.toLowerCase(Locale.ROOT);
        Path javaDir = root.resolve("src/main/java").resolve(pkg.replace('.', '/'));
        Files.createDirectories(javaDir);
        writeText(javaDir.resolve(project.getMainClassName() + ".java"), templateMainJava(project, pkg));
        if (!project.getEvents().isEmpty()) {
            writeText(javaDir.resolve("EventsListener.java"), templateListenerJava(project, pkg));
        }

        return new GeneratedProject(project, root);
    }

    private static String sanitize(String name) {
        String trimmed = (name == null) ? "" : name.trim();
        if (trimmed.isBlank()) return "MyPlugin";
        return trimmed.replaceAll("[^A-Za-z0-9._-]", "");
    }

    private void writeText(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String templateSettingsGradle(String name) {
        return "rootProject.name = '" + name + "'\n";
    }

    private String templateBuildGradle(PluginProject project) {
        Map<String, String> vars = new HashMap<>();
        vars.put("GROUP", project.getGroupId());
        vars.put("VERSION", project.getVersion());

        return templateEngine.render("""
                plugins {
                    id 'java'
                }
                
                group = '${GROUP}'
                version = '${VERSION}'
                
                repositories {
                    mavenCentral()
                    maven { url = 'https://repo.papermc.io/repository/maven-public/' }
                }
                
                dependencies {
                    compileOnly 'io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT'
                }
                
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }
                """, vars);
    }

    private String templatePluginYml(PluginProject project) {
        String pkg = project.getGroupId() + "." + sanitize(project.getName()).toLowerCase(Locale.ROOT);
        String main = pkg + "." + project.getMainClassName();
        String commands = project.getCommands().isEmpty()
                ? ""
                : "\ncommands:\n" + project.getCommands().stream()
                .map(this::pluginYmlCommand)
                .collect(Collectors.joining("\n"));

        Map<String, String> vars = new HashMap<>();
        vars.put("NAME", project.getName());
        vars.put("VERSION", project.getVersion());
        vars.put("MAIN", main);

        String base = templateEngine.render("""
                name: ${NAME}
                version: ${VERSION}
                main: ${MAIN}
                api-version: '1.20'
                """, vars) + commands + "\n";
        return base;
    }

    private String pluginYmlCommand(PluginCommand command) {
        String name = (command.getName() == null) ? "" : command.getName().trim();
        if (name.isBlank()) name = "cmd";
        String desc = (command.getDescription() == null) ? "" : command.getDescription().trim();
        return "  " + name + ":\n    description: \"" + escapeYaml(desc) + "\"";
    }

    private static String escapeYaml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String templateMainJava(PluginProject project, String pkg) {
        String commandHandlers = project.getCommands().isEmpty()
                ? "        getLogger().info(\"No commands configured.\");\n"
                : project.getCommands().stream()
                .map(c -> "        if (getCommand(\"" + c.getName() + "\") != null) getCommand(\"" + c.getName() + "\").setExecutor((sender, command, label, args) -> { sender.sendMessage(\"" + escapeJava(c.getDescription()) + "\"); return true; });\n")
                .collect(Collectors.joining());

        String eventRegister = project.getEvents().isEmpty()
                ? ""
                : "        getServer().getPluginManager().registerEvents(new EventsListener(this), this);\n";

        Map<String, String> vars = new HashMap<>();
        vars.put("PKG", pkg);
        vars.put("CLASS", project.getMainClassName());
        vars.put("NAME", project.getName());
        vars.put("COMMANDS", commandHandlers + eventRegister + "        saveDefaultConfig();\n");

        return templateEngine.render("""
                package ${PKG};
                
                import org.bukkit.plugin.java.JavaPlugin;
                
                public final class ${CLASS} extends JavaPlugin {
                    @Override
                    public void onEnable() {
                        getLogger().info("${NAME} enabled");
                ${COMMANDS}    }
                }
                """, vars);
    }

    private String templateListenerJava(PluginProject project, String pkg) {
        String methods = project.getEvents().stream()
                .map(this::listenerMethod)
                .collect(Collectors.joining("\n\n"));

        Map<String, String> vars = new HashMap<>();
        vars.put("PKG", pkg);
        vars.put("METHODS", methods);

        return templateEngine.render("""
                package ${PKG};
                
                import org.bukkit.event.EventHandler;
                import org.bukkit.event.Listener;
                import org.bukkit.plugin.java.JavaPlugin;
                
                public final class EventsListener implements Listener {
                    private final JavaPlugin plugin;
                
                    public EventsListener(JavaPlugin plugin) {
                        this.plugin = plugin;
                    }
                
                ${METHODS}
                }
                """, vars);
    }

    private String listenerMethod(PluginEventHandler handler) {
        String cls = handler.getEventClass();
        String method = handler.getMethodName().isBlank() ? "onEvent" : handler.getMethodName();
        String msg = escapeJava(handler.getMessage());
        return """
                @EventHandler
                public void %s(%s event) {
                    plugin.getServer().broadcastMessage("%s");
                }""".formatted(method, cls, msg);
    }

    private String templateConfigYml(PluginProject project) {
        Object raw = project.getConfig() == null ? null : project.getConfig().getValues().get("raw");
        if (raw instanceof String s && !s.isBlank()) return s.endsWith("\n") ? s : (s + "\n");
        return """
                # config.yml (generated by PluginCreator)
                greeting: "Hello!"
                """;
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
