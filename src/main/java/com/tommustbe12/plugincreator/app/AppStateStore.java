package com.tommustbe12.plugincreator.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

public final class AppStateStore {
    private static final String KEY_RECENTS = "recentProjects";
    private static final String KEY_LAST = "lastProject";

    private final Preferences prefs = Preferences.userNodeForPackage(AppStateStore.class);

    public List<String> recentProjects() {
        String raw = prefs.get(KEY_RECENTS, "");
        if (raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    public void pushRecent(String jsonPath) {
        List<String> recents = new ArrayList<>(recentProjects());
        recents.remove(jsonPath);
        recents.add(0, jsonPath);
        while (recents.size() > 12) recents.remove(recents.size() - 1);
        prefs.put(KEY_RECENTS, String.join("|", recents));
        prefs.put(KEY_LAST, jsonPath);
    }

    public String lastProject() {
        return prefs.get(KEY_LAST, "");
    }
}

