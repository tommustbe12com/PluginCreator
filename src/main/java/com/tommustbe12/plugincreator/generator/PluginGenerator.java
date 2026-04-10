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

        String body;
        if (cmd.getScratch() != null && cmd.getScratch().getBlocks() != null && !cmd.getScratch().getBlocks().isEmpty()) {
            body = "org.bukkit.entity.Player p = (sender instanceof org.bukkit.entity.Player pl) ? pl : null;\n                "
                    + scratchToJava(cmd.getScratch().getBlocks(), "this", "p", 0);
        } else if (cmd.getActions() != null && !cmd.getActions().isEmpty()) {
            body = cmd.getActions().stream().map(this::actionLine).collect(Collectors.joining("\n                "));
        } else {
            body = "sender.sendMessage(\"" + escapeJava(cmd.getDescription()) + "\");";
        }

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

    private String scratchToJava(java.util.List<com.tommustbe12.plugincreator.model.ScratchBlock> blocks, String pluginExpr, String playerVar, int indent) {
        String pad = " ".repeat(Math.max(0, indent));
        return blocks.stream()
                .map(b -> pad + scratchBlockToJava(b, pluginExpr, playerVar, indent))
                .collect(Collectors.joining("\n" + pad));
    }

    private String scratchBlockToJava(com.tommustbe12.plugincreator.model.ScratchBlock block, String pluginExpr, String playerVar, int indent) {
        var p = block.getParams();
        return switch (block.getType()) {
            case SAY_TEXT -> {
                String target = p.getOrDefault("target", "PLAYER").toUpperCase(Locale.ROOT);
                String text = escapeJava(p.getOrDefault("text", "Hello!"));
                if ("ALL_PLAYERS".equals(target)) {
                    yield "for (var pl : " + pluginExpr + ".getServer().getOnlinePlayers()) pl.sendMessage(\"" + text + "\");";
                }
                yield "if (" + playerVar + " != null) " + playerVar + ".sendMessage(\"" + text + "\");";
            }
            case SET_GAMEMODE -> {
                String gm = escapeJava(p.getOrDefault("gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
                yield "if (" + playerVar + " != null) " + playerVar + ".setGameMode(org.bukkit.GameMode.valueOf(\"" + gm + "\"));";
            }
            case OPEN_GUI -> "if (" + playerVar + " != null) GuiManager.openMain(" + playerVar + ");";
            case RUN_CONSOLE_COMMAND -> {
                String cmd = escapeJava(p.getOrDefault("command", "say Hello"));
                yield pluginExpr + ".getServer().dispatchCommand(" + pluginExpr + ".getServer().getConsoleSender(), \"" + cmd + "\");";
            }
            case REPEAT_C -> {
                int count = safeInt(p.getOrDefault("count", "5"), 5);
                String inner = scratchToJava(block.getChildren(), pluginExpr, playerVar, indent + 4);
                yield "for (int i=0;i<" + count + ";i++) {\n" + " ".repeat(indent + 4) + inner + "\n" + " ".repeat(indent) + "}";
            }
            case IF_C -> {
                String cond = p.getOrDefault("cond", "PLAYER_PRESENT");
                String test = switch (cond) {
                    case "HAS_PERMISSION" -> "(" + playerVar + " != null && " + playerVar + ".hasPermission(\"" + escapeJava(p.getOrDefault("perm", "plugin.use")) + "\"))";
                    default -> "(" + playerVar + " != null)";
                };
                String inner = scratchToJava(block.getChildren(), pluginExpr, playerVar, indent + 4);
                yield "if " + test + " {\n" + " ".repeat(indent + 4) + inner + "\n" + " ".repeat(indent) + "}";
            }
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
        String flow = "";
        if (handler.getProgram() != null && handler.getProgram().getBlocks() != null && !handler.getProgram().getBlocks().isEmpty()) {
            flow = """
                    org.bukkit.entity.Player p = (event instanceof org.bukkit.event.player.PlayerEvent pe) ? pe.getPlayer() : null;
                    %s
                    """.formatted(flowToJava(handler.getProgram(), "plugin", "p"));
        }
        return """
                @EventHandler
                public void %s(%s event) {
                    %s
                    plugin.getServer().broadcastMessage("%s");
                }""".formatted(method, cls, flow, msg);
    }

    private String flowToJava(com.tommustbe12.plugincreator.model.FlowProgram program, String pluginExpr, String playerVar) {
        return program.getBlocks().stream()
                .map(b -> flowBlockToJava(b, pluginExpr, playerVar))
                .collect(Collectors.joining("\n                    "));
    }

    private String flowBlockToJava(com.tommustbe12.plugincreator.model.FlowBlock block, String pluginExpr, String playerVar) {
        var p = block.getParams();
        return switch (block.getType()) {
            case SAY_TO_PLAYER -> {
                String text = escapeJava(p.getOrDefault("text", "Hello!"));
                yield "if (" + playerVar + " != null) " + playerVar + ".sendMessage(\"" + text + "\");";
            }
            case BROADCAST -> {
                String text = escapeJava(p.getOrDefault("text", "Hello!"));
                yield pluginExpr + ".getServer().broadcastMessage(\"" + text + "\");";
            }
            case SET_GAMEMODE -> {
                String gm = escapeJava(p.getOrDefault("gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
                yield "if (" + playerVar + " != null) " + playerVar + ".setGameMode(org.bukkit.GameMode.valueOf(\"" + gm + "\"));";
            }
            case GIVE_ITEM -> {
                String mat = escapeJava(p.getOrDefault("material", "DIAMOND").toUpperCase(Locale.ROOT));
                int amt = safeInt(p.getOrDefault("amount", "1"), 1);
                yield "if (" + playerVar + " != null) " + playerVar + ".getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.valueOf(\"" + mat + "\"), " + amt + "));";
            }
            case TELEPORT_PLAYER -> {
                String world = escapeJava(p.getOrDefault("world", "world"));
                double x = safeDouble(p.getOrDefault("x", "0"), 0);
                double y = safeDouble(p.getOrDefault("y", "80"), 80);
                double z = safeDouble(p.getOrDefault("z", "0"), 0);
                yield "if (" + playerVar + " != null) { var w = " + pluginExpr + ".getServer().getWorld(\"" + world + "\"); if (w != null) " + playerVar + ".teleport(new org.bukkit.Location(w, " + x + ", " + y + ", " + z + ")); }";
            }
            case OPEN_GUI -> "if (" + playerVar + " != null) GuiManager.openMain(" + playerVar + ");";
            case RUN_CONSOLE_COMMAND -> {
                String cmd = escapeJava(p.getOrDefault("command", "say Hello"));
                yield pluginExpr + ".getServer().dispatchCommand(" + pluginExpr + ".getServer().getConsoleSender(), \"" + cmd + "\");";
            }
        };
    }

    private static int safeInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static double safeDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ignored) { return fallback; }
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
