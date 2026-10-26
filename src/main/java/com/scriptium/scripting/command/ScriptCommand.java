package com.scriptium.scripting.command;

import org.graalvm.polyglot.Value;

import java.util.List;

public final class ScriptCommand {

    private final List<CommandArgument> arguments;

    private final CommandManager manager;
    private final String scriptName;

    private final String name;
    private final Value callback;

    public ScriptCommand(CommandManager manager, String scriptName, String name, Value callback,
            List<CommandArgument> arguments) {

        this.manager = manager;
        this.scriptName = scriptName;
        this.name = name;
        this.callback = callback;

        this.arguments = List.copyOf(arguments);
    }

    public List<CommandArgument> getArguments() {
        return arguments;
    }

    public String getScriptName() {
        return scriptName;
    }

    public String getName() {
        return name;
    }

    public Value getCallback() {
        return callback;
    }

    public void unregister() {
        manager.unregister(this);
    }
}