package com.scriptium.scripting.event;

import java.util.Objects;

public final class ScriptEventBus {

    private static volatile EventManager eventManager;

    private ScriptEventBus() {
    }

    public static void initialize(EventManager manager) {
        eventManager = Objects.requireNonNull(manager);
    }

    public static void emit(String event, Object... arguments) {
        EventManager manager = eventManager;

        if (manager != null) {
            manager.emit(event, arguments);
        }
    }
}