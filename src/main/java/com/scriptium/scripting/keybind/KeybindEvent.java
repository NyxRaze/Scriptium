package com.scriptium.scripting.keybind;

public final class KeybindEvent {

    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;
    public static final int SUPER = 8;

    private final int key;
    private final int scanCode;
    private final int modifiers;

    public KeybindEvent(int key, int scanCode, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
    }

    public int getKey() {
        return key;
    }

    public int getScanCode() {
        return scanCode;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isShiftDown() {
        return (modifiers & SHIFT) != 0;
    }

    public boolean isControlDown() {
        return (modifiers & CONTROL) != 0;
    }

    public boolean isAltDown() {
        return (modifiers & ALT) != 0;
    }

    public boolean isSuperDown() {
        return (modifiers & SUPER) != 0;
    }
}