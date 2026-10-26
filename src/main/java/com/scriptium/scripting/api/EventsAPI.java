package com.scriptium.scripting.api;

import org.graalvm.polyglot.Value;

import com.scriptium.scripting.event.EventManager;

public final class EventsAPI {

    private final EventManager eventManager;
    private final String scriptName;

    public EventsAPI(EventManager eventManager, String scriptName) {
        this.eventManager = eventManager;
        this.scriptName = scriptName;
    }

    public void on(String event, Value callback) {
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("Event callback must be a function.");
        }

        eventManager.on(scriptName, event, callback);
    }

    public void off(String event, Value callback) {
        eventManager.off(scriptName, event, callback);
    }
}