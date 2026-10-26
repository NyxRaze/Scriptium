package com.scriptium.scripting.api;

import com.scriptium.scripting.keybind.KeybindManager;
import com.scriptium.scripting.keybind.Keybind;

import org.graalvm.polyglot.Value;

public final class KeybindsAPI {

    private final KeybindManager manager;
    private final String scriptName;

    public KeybindsAPI(KeybindManager manager, String scriptName) {
        this.manager = manager;
        this.scriptName = scriptName;
    }

    public Keybind register(String name, int key, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Keybind callback must be a function.");
        }

        return manager.register(scriptName, name, key, callback);
    }

    public void changeKey(Keybind keybind, int key) {
        if (keybind == null) {
            throw new IllegalArgumentException("Keybind cannot be null.");
        }

        manager.changeKey(scriptName, keybind, key);
    }
}