
package com.scriptium.scripting.api;

import com.scriptium.mixin.accessor.KeyMappingAccessor;
import com.scriptium.scripting.input.InputManager;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class InputAPI {

    private final String scriptName;
    private final InputManager manager;

    public InputAPI(String scriptName, InputManager manager) {
        this.scriptName = scriptName;
        this.manager = manager;
    }

    public InputAPI scope(String name) {
        if (name == null || name.isBlank() || name.contains("::")) {
            throw new IllegalArgumentException("Input scope name must be non-blank and cannot contain '::'");
        }
        return new InputAPI(scriptName + "::" + name, manager);
    }

    private InputConstants.Key resolve(KeyMapping mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("KeyMapping cannot be null");
        }

        return ((KeyMappingAccessor) (Object) mapping).scriptium$getKey();
    }

    public void tap(InputConstants.Key key) {
        manager.tap(scriptName, key);
    }

    public void tap(InputConstants.Key key, long durationMs) {
        manager.tap(scriptName, key, durationMs);
    }

    public void tap(InputConstants.Key key, long durationMs, boolean restore) {
        manager.tap(scriptName, key, durationMs, restore);
    }

    public void tap(KeyMapping mapping) {
        tap(resolve(mapping));
    }

    public void tap(KeyMapping mapping, long durationMs) {
        tap(resolve(mapping), durationMs);
    }

    public void tap(KeyMapping mapping, long durationMs, boolean restore) {
        tap(resolve(mapping), durationMs, restore);
    }

    public void press(InputConstants.Key key) {
        manager.press(scriptName, key);
    }

    public void press(KeyMapping mapping) {
        press(resolve(mapping));
    }

    public void release(InputConstants.Key key) {
        manager.release(scriptName, key);
    }

    public void release(KeyMapping mapping) {
        release(resolve(mapping));
    }

    public boolean isReallyDown(InputConstants.Key key) {
        return manager.isReallyDown(key);
    }

    public boolean isReallyDown(KeyMapping mapping) {
        return isReallyDown(resolve(mapping));
    }

    public boolean isFiringSynthetic() {
        return manager.isSynthetic();
    }

    public boolean isDown(InputConstants.Key key) {
        return manager.isDown(key);
    }

    public boolean isDown(KeyMapping mapping) {
        return isDown(resolve(mapping));
    }

    public void block(InputConstants.Key key) {
        manager.block(scriptName, key);
    }

    public void block(KeyMapping mapping) {
        block(resolve(mapping));
    }

    public void blockFor(InputConstants.Key key, long durationMs) {
        manager.blockFor(scriptName, key, durationMs);
    }

    public void blockFor(KeyMapping mapping, long durationMs) {
        blockFor(resolve(mapping), durationMs);
    }

    public void unblock(InputConstants.Key key) {
        manager.unblock(scriptName, key);
    }

    public void unblock(KeyMapping mapping) {
        unblock(resolve(mapping));
    }

    public void blockUntilRelease(InputConstants.Key key) {
        manager.blockUntilRelease(scriptName, key);
    }

    public void blockUntilRelease(KeyMapping mapping) {
        blockUntilRelease(resolve(mapping));
    }

    public void lockCursor(double x, double y) {
        manager.lockCursor(x, y);
    }

    public void unlockCursor() {
        manager.unlockCursor();
    }

    public void releaseAll() {
        manager.releaseAll(scriptName);
    }
}
