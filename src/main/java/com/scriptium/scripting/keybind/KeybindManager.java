package com.scriptium.scripting.keybind;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KeybindManager {

    private final Map<String, List<Keybind>> keybinds = new HashMap<>();

    private final Map<Integer, Boolean> keyStates = new HashMap<>();

    public void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::handleKeys);
    }

    public Keybind register(String scriptName, String name, int key, Value callback) {
        Keybind keybind = new Keybind(this, scriptName, name, key, callback);

        keybinds.computeIfAbsent(scriptName, ignored -> new ArrayList<>()).add(keybind);

        return keybind;
    }

    public void unregister(Keybind keybind) {
        List<Keybind> scriptKeybinds = keybinds.get(keybind.getScriptName());

        if (scriptKeybinds == null) {
            return;
        }

        scriptKeybinds.remove(keybind);

        if (scriptKeybinds.isEmpty()) {
            keybinds.remove(keybind.getScriptName());
        }
    }

    public void changeKey(String scriptName, Keybind keybind, int key) {
        if (key < GLFW.GLFW_KEY_UNKNOWN || key > GLFW.GLFW_KEY_LAST) {
            throw new IllegalArgumentException("Keybind key must be a valid GLFW key code.");
        }

        if (!scriptName.equals(keybind.getScriptName()) || !isRegistered(keybind)) {
            throw new IllegalArgumentException("Keybind is not registered to this script.");
        }

        int previousKey = keybind.getKey();

        if (previousKey == key) {
            return;
        }

        keybind.setKeyInternal(key);

        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            long window = Minecraft.getInstance().getWindow().handle();
            keyStates.putIfAbsent(key, GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS);
        }

        if (previousKey != GLFW.GLFW_KEY_UNKNOWN && !isKeyBound(previousKey)) {
            keyStates.remove(previousKey);
        }
    }

    private boolean isRegistered(Keybind target) {
        List<Keybind> scriptKeybinds = keybinds.get(target.getScriptName());
        return scriptKeybinds != null && scriptKeybinds.contains(target);
    }

    public void removeScript(String scriptName) {
        keybinds.remove(scriptName);
    }

    private boolean isKeyBound(int key) {
        for (List<Keybind> scriptKeybinds : keybinds.values()) {
            for (Keybind keybind : scriptKeybinds) {
                if (keybind.getKey() == key) {
                    return true;
                }
            }
        }

        return false;
    }

    private void handleKeys(Minecraft mc) {
        long window = mc.getWindow().handle();

        /*
         * First determine the state of every key that
         * currently has at least one keybind.
         */
        Map<Integer, Boolean> currentStates = new HashMap<>();

        for (List<Keybind> scriptKeybinds : new ArrayList<>(keybinds.values())) {
            for (Keybind keybind : new ArrayList<>(scriptKeybinds)) {
                int key = keybind.getKey();

                if (key != GLFW.GLFW_KEY_UNKNOWN) {
                    currentStates.computeIfAbsent(key, ignored -> GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS);
                }
            }
        }

        /*
         * Process newly pressed keys.
         */
        for (Map.Entry<Integer, Boolean> entry : currentStates.entrySet()) {
            int key = entry.getKey();
            boolean pressed = entry.getValue();

            boolean previouslyPressed = keyStates.getOrDefault(key, false);

            if (pressed && !previouslyPressed) {
                int scanCode = GLFW.glfwGetKeyScancode(key);

                int modifiers = getModifiers(window);

                KeybindEvent event = new KeybindEvent(key, scanCode, modifiers);

                executeKeybinds(key, event);
            }

            keyStates.put(key, pressed);
        }

        keyStates.keySet().retainAll(currentStates.keySet());
    }

    private void executeKeybinds(int key, KeybindEvent event) {
        /*
         * Snapshot the collections so callbacks can safely
         * unregister keybinds while we are iterating.
         */
        for (List<Keybind> scriptKeybinds : new ArrayList<>(keybinds.values())) {
            for (Keybind keybind : new ArrayList<>(scriptKeybinds)) {
                if (keybind.getKey() == key) {
                    keybind.execute(event);
                }
            }
        }
    }

    private int getModifiers(long window) {
        int modifiers = 0;

        if (isModifierDown(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            modifiers |= KeybindEvent.SHIFT;
        }

        if (isModifierDown(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            modifiers |= KeybindEvent.CONTROL;
        }

        if (isModifierDown(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) {
            modifiers |= KeybindEvent.ALT;
        }

        if (isModifierDown(window, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)) {
            modifiers |= KeybindEvent.SUPER;
        }

        return modifiers;
    }

    private boolean isModifierDown(long window, int leftKey, int rightKey) {
        return GLFW.glfwGetKey(window, leftKey) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, rightKey) == GLFW.GLFW_PRESS;
    }
}