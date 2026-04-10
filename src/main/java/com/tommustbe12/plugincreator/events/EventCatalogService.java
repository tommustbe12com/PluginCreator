package com.tommustbe12.plugincreator.events;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class EventCatalogService {
    public List<BukkitEventInfo> listAllEvents() {
        try (var scan = new ClassGraph()
                .enableClassInfo()
                .acceptPackages("org.bukkit.event")
                .scan()) {

            return scan.getSubclasses("org.bukkit.event.Event")
                    .stream()
                    .filter(ci -> isConcrete(ci))
                    .map(ci -> new BukkitEventInfo(ci.getName(), toDisplayName(ci.getSimpleName()), toSuggestedHandler(ci.getSimpleName())))
                    .sorted(Comparator.comparing(BukkitEventInfo::displayName))
                    .collect(Collectors.toList());
        }
    }

    private static boolean isConcrete(ClassInfo ci) {
        if (ci.isAbstract()) return false;
        if (ci.isInterface()) return false;
        String name = ci.getName();
        if (!name.startsWith("org.bukkit.event.")) return false;
        if (!name.endsWith("Event")) return false;
        return true;
    }

    public static String toDisplayName(String simpleName) {
        String base = simpleName;
        if (base.endsWith("Event")) base = base.substring(0, base.length() - "Event".length());
        String spaced = base.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        if (spaced.isBlank()) spaced = simpleName;
        return "On " + spaced;
    }

    public static String toSuggestedHandler(String simpleName) {
        String base = simpleName;
        if (base.endsWith("Event")) base = base.substring(0, base.length() - "Event".length());
        if (base.isBlank()) base = "Event";
        return "on" + base;
    }

    public record BukkitEventInfo(String className, String displayName, String suggestedMethodName) {
        public boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            String q = query.toLowerCase(Locale.ROOT).trim();
            return displayName.toLowerCase(Locale.ROOT).contains(q)
                    || className.toLowerCase(Locale.ROOT).contains(q);
        }
    }
}
