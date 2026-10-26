package com.scriptium.scripting.api;

import com.scriptium.scripting.ScriptManager;

import org.graalvm.polyglot.Value;
import java.util.List;

public final class ScriptManagerAPI {

    private final ScriptManager manager;
    private final String ownerScript;

    public ScriptManagerAPI(ScriptManager manager, String ownerScript) {
        this.manager = manager;
        this.ownerScript = ownerScript;
    }

    public boolean load(String name) {
        return manager.load(name);
    }

    public boolean unload(String name) {
        return manager.unload(name);
    }

    public boolean reload(String name) {
        return manager.reload(name);
    }

    public boolean isLoaded(String name) {
        return manager.isLoaded(name);
    }

    public List<String> list() {
        return manager.getScriptNames();
    }

    public Value importScript(String name) {
        return manager.importScript(ownerScript, name);
    }

    public Value importScriptFrom(String name, String importerPath) {
        return manager.importScript(ownerScript, name, importerPath);
    }
}