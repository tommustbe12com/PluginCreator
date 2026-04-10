package com.tommustbe12.plugincreator.generator;

import com.tommustbe12.plugincreator.model.PluginCommand;
import com.tommustbe12.plugincreator.model.PluginEventHandler;
import com.tommustbe12.plugincreator.model.PluginProject;
import com.tommustbe12.plugincreator.model.GuiScreen;
import com.tommustbe12.plugincreator.model.GuiSlot;

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
        if (!project.getGuis().isEmpty()) {
            writeText(javaDir.resolve("GuiManager.java"), templateGuiManager(project, pkg));
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
                .map(this::commandExecutor)
                .collect(Collectors.joining());

        String eventRegister = project.getEvents().isEmpty()
                ? ""
                : "        getServer().getPluginManager().registerEvents(new EventsListener(this), this);\n";

        String guiInit = project.getGuis().isEmpty()
                ? ""
                : "        GuiManager.init(this);\n";

        Map<String, String> vars = new HashMap<>();
        vars.put("PKG", pkg);
        vars.put("CLASS", project.getMainClassName());
        vars.put("NAME", project.getName());
        vars.put("COMMANDS", commandHandlers + eventRegister + guiInit + "        saveDefaultConfig();\n");

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

    private String commandExecutor(com.tommustbe12.plugincreator.model.PluginCommand cmd) {
        String name = (cmd.getName() == null) ? "" : cmd.getName().trim();
        if (name.isBlank()) name = "cmd";

        String body = (cmd.getActions() == null || cmd.getActions().isEmpty())
                ? "sender.sendMessage(\"" + escapeJava(cmd.getDescription()) + "\");"
                : cmd.getActions().stream().map(this::actionLine).collect(Collectors.joining("\n                "));

        return """
                if (getCommand("%s") != null) getCommand("%s").setExecutor((sender, command, label, args) -> {
                    %s
                    return true;
                });
                """.formatted(name, name, body);
    }

    private String actionLine(com.tommustbe12.plugincreator.model.CommandAction action) {
        String text = escapeJava(action.getText());
        return switch (action.getType()) {
            case BROADCAST -> "getServer().broadcastMessage(\"" + text + "\");";
            case SEND_MESSAGE -> "sender.sendMessage(\"" + text + "\");";
            case RUN_CONSOLE_COMMAND -> "getServer().dispatchCommand(getServer().getConsoleSender(), \"" + text + "\");";
            case SET_GAMEMODE -> "if (sender instanceof org.bukkit.entity.Player p) p.setGameMode(org.bukkit.GameMode.valueOf(\"" + escapeJava(action.getGamemode()) + "\"));";
            case TELEPORT_SELF -> "if (sender instanceof org.bukkit.entity.Player p) { var w = getServer().getWorld(\"" + escapeJava(action.getWorld()) + "\"); if (w != null) p.teleport(new org.bukkit.Location(w, " + action.getX() + ", " + action.getY() + ", " + action.getZ() + ")); }";
            case GIVE_ITEM -> "if (sender instanceof org.bukkit.entity.Player p) p.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(\"" + escapeJava(action.getMaterial()) + "\"), " + action.getAmount() + "));";
        };
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
                %s
                
                public final class EventsListener implements Listener {
                    private final JavaPlugin plugin;
                
                    public EventsListener(JavaPlugin plugin) {
                        this.plugin = plugin;
                    }
                
                ${METHODS}
                }
                """.formatted(eventImports(project)), vars);
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

    private String templateGuiManager(PluginProject project, String pkg) {
        GuiScreen gui = project.getGuis().getFirst();
        int size = gui.getRows() * 9;
        String title = escapeJava(gui.getTitle());

        String slotsInit = gui.getSlots().stream()
                .map(s -> initSlot(s))
                .collect(Collectors.joining("\n"));

        String clickCases = gui.getSlots().stream()
                .filter(s -> s.getOnClickActions() != null && !s.getOnClickActions().isEmpty())
                .map(this::clickCase)
                .collect(Collectors.joining("\n"));

        Map<String, String> vars = new HashMap<>();
        vars.put("PKG", pkg);
        vars.put("TITLE", title);
        vars.put("SIZE", String.valueOf(size));
        vars.put("SLOTS", slotsInit);
        vars.put("CASES", clickCases.isBlank() ? "" : clickCases);

        return templateEngine.render("""
                package ${PKG};
                
                import org.bukkit.Bukkit;
                import org.bukkit.Material;
                import org.bukkit.entity.Player;
                import org.bukkit.event.EventHandler;
                import org.bukkit.event.Listener;
                import org.bukkit.event.inventory.InventoryClickEvent;
                import org.bukkit.inventory.Inventory;
                import org.bukkit.inventory.ItemStack;
                import org.bukkit.inventory.meta.ItemMeta;
                import org.bukkit.plugin.java.JavaPlugin;
                
                import java.util.List;
                
                public final class GuiManager implements Listener {
                    private static JavaPlugin plugin;
                    private static Inventory main;
                
                    public static void init(JavaPlugin plugin) {
                        GuiManager.plugin = plugin;
                        main = Bukkit.createInventory(null, ${SIZE}, "${TITLE}");
                ${SLOTS}
                        plugin.getServer().getPluginManager().registerEvents(new GuiManager(), plugin);
                    }
                
                    public static void openMain(Player player) {
                        if (main == null) return;
                        player.openInventory(main);
                    }
                
                    @EventHandler
                    public void onClick(InventoryClickEvent e) {
                        if (main == null) return;
                        if (!e.getView().getTopInventory().equals(main)) return;
                        e.setCancelled(true);
                        if (!(e.getWhoClicked() instanceof Player p)) return;
                        int slot = e.getRawSlot();
                        switch (slot) {
                ${CASES}
                            default -> {}
                        }
                    }
                
                    private static ItemStack item(Material material, String name, List<String> lore) {
                        ItemStack it = new ItemStack(material);
                        ItemMeta meta = it.getItemMeta();
                        if (meta != null) {
                            if (name != null && !name.isBlank()) meta.setDisplayName(name);
                            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
                            it.setItemMeta(meta);
                        }
                        return it;
                    }
                }
                """, vars);
    }

    private String initSlot(GuiSlot slot) {
        String material = escapeJava(slot.getMaterial());
        String name = escapeJava(slot.getDisplayName());
        String lore = (slot.getLore() == null || slot.getLore().isEmpty())
                ? "List.of()"
                : "List.of(" + slot.getLore().stream().map(s -> "\"" + escapeJava(s) + "\"").collect(Collectors.joining(", ")) + ")";
        return "        main.setItem(" + slot.getIndex() + ", item(Material.valueOf(\"" + material + "\"), \"" + name + "\", " + lore + "));";
    }

    private String clickCase(GuiSlot slot) {
        String actions = slot.getOnClickActions().stream()
                .map(a -> guiActionLine(a))
                .collect(Collectors.joining("\n                    "));
        return "            case " + slot.getIndex() + " -> {\n                    " + actions + "\n                }";
    }

    private String guiActionLine(com.tommustbe12.plugincreator.model.CommandAction action) {
        String text = escapeJava(action.getText());
        return switch (action.getType()) {
            case BROADCAST -> "plugin.getServer().broadcastMessage(\"" + text + "\");";
            case SEND_MESSAGE -> "p.sendMessage(\"" + text + "\");";
            case RUN_CONSOLE_COMMAND -> "plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), \"" + text + "\");";
            case SET_GAMEMODE -> "p.setGameMode(org.bukkit.GameMode.valueOf(\"" + escapeJava(action.getGamemode()) + "\"));";
            case TELEPORT_SELF -> "{ var w = plugin.getServer().getWorld(\"" + escapeJava(action.getWorld()) + "\"); if (w != null) p.teleport(new org.bukkit.Location(w, " + action.getX() + ", " + action.getY() + ", " + action.getZ() + ")); }";
            case GIVE_ITEM -> "p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.valueOf(\"" + escapeJava(action.getMaterial()) + "\"), " + action.getAmount() + "));";
        };
    }

    private String eventImports(PluginProject project) {
        String imports = project.getEvents().stream()
                .map(PluginEventHandler::getEventClass)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .map(s -> "import " + s + ";")
                .collect(Collectors.joining("\n"));
        return imports.isBlank() ? "" : (imports + "\n");
    }

    private String templateConfigYml(PluginProject project) {
        if (project.getConfig() != null && project.getConfig().getRaw() != null && !project.getConfig().getRaw().isBlank()) {
            String s = project.getConfig().getRaw();
            return s.endsWith("\n") ? s : (s + "\n");
        }
        if (project.getConfig() != null && project.getConfig().getValues() != null && !project.getConfig().getValues().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("# config.yml (generated by PluginCreator)\n");
            for (var e : project.getConfig().getValues().entrySet()) {
                sb.append(e.getKey()).append(": ").append(yamlScalar(e.getValue())).append("\n");
            }
            return sb.toString();
        }
        return """
                # config.yml (generated by PluginCreator)
                greeting: "Hello!"
                """;
    }

    private static String yamlScalar(String value) {
        if (value == null) return "\"\"";
        String v = value.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) return v.toLowerCase(Locale.ROOT);
        if (v.matches("-?\\d+(\\.\\d+)?")) return v;
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
