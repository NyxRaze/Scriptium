package com.scriptium.scripting.keybind;

import org.graalvm.polyglot.Value;

public final class Keybind {

    private final KeybindManager manager;
    private final String scriptName;

    private final String name;
    private volatile int key;
    private final Value callback;

    private boolean enabled = true;

    public Keybind(KeybindManager manager, String scriptName, String name, int key, Value callback) {
        this.manager = manager;
        this.scriptName = scriptName;
        this.name = name;
        this.key = key;
        this.callback = callback;
    }

    public String getName() {
        return name;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        manager.changeKey(scriptName, this, key);
    }

    void setKeyInternal(int key) {
        this.key = key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void unregister() {
        manager.unregister(this);
    }

    public void execute(Object event) {
        if (!enabled) {
            return;
        }

        try {
            if (callback.canExecute()) {
                callback.execute(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getScriptName() {
        return scriptName;
    }
}