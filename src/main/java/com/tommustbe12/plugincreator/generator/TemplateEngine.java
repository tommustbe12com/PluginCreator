package com.tommustbe12.plugincreator.generator;

import java.util.Map;

public final class TemplateEngine {
    public String render(String template, Map<String, String> vars) {
        String out = template;
        for (var entry : vars.entrySet()) {
            out = out.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }
}

