
package com.scriptium.scripting.command;

import java.util.List;

public record CommandArgument(String name, String type, List<String> suggestions, Double min, Double max) {

    public CommandArgument {
        if (name == null || !name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid argument name: " + name);
        }

        if (!List.of("string", "word", "greedyString", "integer", "decimal").contains(type)) {
            throw new IllegalArgumentException("Unsupported argument type: " + type);
        }

        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);

        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Argument min cannot exceed max.");
        }

        if (min != null && !List.of("integer", "decimal").contains(type)) {
            throw new IllegalArgumentException("min is only supported for numeric arguments.");
        }

        if (max != null && !List.of("integer", "decimal").contains(type)) {
            throw new IllegalArgumentException("max is only supported for numeric arguments.");
        }
    }

    public CommandArgument(String name, String type, List<String> suggestions) {
        this(name, type, suggestions, null, null);
    }
}
