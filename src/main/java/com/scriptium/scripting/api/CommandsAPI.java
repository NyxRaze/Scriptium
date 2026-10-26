package com.scriptium.scripting.api;

import com.scriptium.scripting.command.CommandArgument;
import com.scriptium.scripting.command.CommandManager;
import com.scriptium.scripting.command.ScriptCommand;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

public final class CommandsAPI {

    private final CommandManager manager;
    private final String scriptName;

    public CommandsAPI(CommandManager manager, String scriptName) {
        this.manager = manager;
        this.scriptName = scriptName;
    }

    public ScriptCommand register(String name, Value callback) {
        return manager.register(scriptName, name, List.of(), callback);
    }

    public ScriptCommand register(String name, Value definitions, Value callback) {
        if (!definitions.hasArrayElements()) {
            throw new IllegalArgumentException("Argument definitions must be an array.");
        }

        List<CommandArgument> arguments = new ArrayList<>();

        for (long i = 0; i < definitions.getArraySize(); i++) {
            Value definition = definitions.getArrayElement(i);

            String argumentName = definition.getMember("name").asString();
            String type = definition.getMember("type").asString();

            List<String> suggestions = new ArrayList<>();

            Value suggestionValues = definition.getMember("suggestions");

            if (suggestionValues != null && suggestionValues.hasArrayElements()) {
                for (long j = 0; j < suggestionValues.getArraySize(); j++) {
                    suggestions.add(suggestionValues.getArrayElement(j).asString());
                }
            }

            Value minValue = definition.getMember("min");
            Value maxValue = definition.getMember("max");

            Double min = minValue != null && !minValue.isNull() ? minValue.asDouble() : null;
            Double max = maxValue != null && !maxValue.isNull() ? maxValue.asDouble() : null;

            arguments.add(new CommandArgument(argumentName, type, suggestions, min, max));
        }

        return manager.register(scriptName, name, arguments, callback);
    }
}