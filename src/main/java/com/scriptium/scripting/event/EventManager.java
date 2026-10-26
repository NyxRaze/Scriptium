package com.scriptium.scripting.event;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventManager {

    private final Map<String, Map<String, List<Value>>> listeners = new HashMap<>();

    public EventManager() {
        ClientReceiveMessageEvents.CHAT
                .register((message, playerChatMessage, sender, boundChatType, timeStamp) -> fire("chat",
                        new ChatEvent(message)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (mc.player != null) {
                fire("join", new JoinEvent(mc.player));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            if (mc.player != null) {
                fire("leave", new LeaveEvent(mc.player));
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> fire("tick", new TickEvent(mc)));
    }

    public void on(String scriptName, String event, Value callback) {
        listeners.computeIfAbsent(scriptName, ignored -> new HashMap<>())
                .computeIfAbsent(event, ignored -> new ArrayList<>()).add(callback);
    }

    public void off(String scriptName, String event, Value callback) {
        Map<String, List<Value>> scriptListeners = listeners.get(scriptName);

        if (scriptListeners == null) {
            return;
        }

        List<Value> eventListeners = scriptListeners.get(event);

        if (eventListeners == null) {
            return;
        }

        eventListeners.remove(callback);

        if (eventListeners.isEmpty()) {
            scriptListeners.remove(event);
        }

        if (scriptListeners.isEmpty()) {
            listeners.remove(scriptName);
        }
    }

    public void removeScript(String scriptName) {
        listeners.remove(scriptName);
    }

    private void fire(String event, Object... arguments) {
        for (Map<String, List<Value>> scriptListeners : new ArrayList<>(listeners.values())) {
            List<Value> callbacks = scriptListeners.get(event);

            if (callbacks == null) {
                continue;
            }

            for (Value callback : new ArrayList<>(callbacks)) {
                try {
                    if (callback.canExecute()) {
                        callback.execute(arguments);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void emit(String event, Object... arguments) {
        fire(event, arguments);
    }
}