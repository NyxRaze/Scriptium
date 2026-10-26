package com.scriptium.scripting.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class InputManager {

    private static final long DEFAULT_TAP_MS = 60L;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Scriptium-Input");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile InputManager instance;

    private final Set<Integer> blockedKeys = ConcurrentHashMap.newKeySet();
    private final Set<Integer> blockedMouseButtons = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Set<InputConstants.Key>> blockedByScript = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<InputConstants.Key>> watchedByScript = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<InputConstants.Key>> pressedByScript = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Tap> activeTaps = new ConcurrentHashMap<>();
    private final Set<InputConstants.Key> restoreAfterTaps = ConcurrentHashMap.newKeySet();
    private final AtomicLong inputIds = new AtomicLong();
    private final ThreadLocal<Boolean> firingSynthetic = ThreadLocal.withInitial(() -> false);

    private volatile boolean cursorLocked;
    private volatile double lockedCursorX;
    private volatile double lockedCursorY;

    private record Tap(long id, String scriptName, InputConstants.Key key, boolean restore) {
    }

    public void initialize() {
        instance = this;
        ClientTickEvents.END_CLIENT_TICK.register(client -> pollReleaseWatchers());
    }

    public static boolean shouldBlockGlobal(InputConstants.Key key) {
        InputManager manager = instance;
        return manager != null && manager.shouldBlock(key);
    }

    public static boolean shouldBlockKey(int keyCode) {
        InputManager manager = instance;
        return manager != null && !manager.firingSynthetic.get() && manager.blockedKeys.contains(keyCode);
    }

    public static boolean shouldBlockMouse(int button) {
        InputManager manager = instance;
        return manager != null && !manager.firingSynthetic.get() && manager.blockedMouseButtons.contains(button);
    }

    public static boolean isCursorLocked() {
        InputManager manager = instance;
        return manager != null && manager.cursorLocked;
    }

    public static double getLockedCursorX() {
        InputManager manager = instance;
        return manager == null ? 0.0 : manager.lockedCursorX;
    }

    public static double getLockedCursorY() {
        InputManager manager = instance;
        return manager == null ? 0.0 : manager.lockedCursorY;
    }

    public boolean isSynthetic() {
        return firingSynthetic.get();
    }

    public void tap(String owner, InputConstants.Key key) {
        tap(owner, key, DEFAULT_TAP_MS, false);
    }

    public void tap(String owner, InputConstants.Key key, long durationMs) {
        tap(owner, key, durationMs, false);
    }

    public void tap(String owner, InputConstants.Key key, long durationMs, boolean restore) {
        requireOwner(owner);
        requireKey(key);

        Tap tap = new Tap(inputIds.incrementAndGet(), owner, key, restore);
        activeTaps.put(tap.id(), tap);
        if (restore) {
            restoreAfterTaps.add(key);
        }
        fireRaw(key, GLFW.GLFW_PRESS);

        Minecraft client = Minecraft.getInstance();
        SCHEDULER.schedule(
                () -> client.execute(() -> finishTap(tap)),
                durationMs,
                TimeUnit.MILLISECONDS);
    }

    private void finishTap(Tap tap) {
        if (activeTaps.remove(tap.id()) == null) {
            return;
        }
        releaseIfUnowned(tap.key());
    }

    public void press(String owner, InputConstants.Key key) {
        requireOwner(owner);
        requireKey(key);
        if (track(pressedByScript, owner).add(key)) {
            fireRaw(key, GLFW.GLFW_PRESS);
        }
    }

    public void release(String owner, InputConstants.Key key) {
        requireOwner(owner);
        requireKey(key);
        removeTrackedKey(pressedByScript, owner, key);
        releaseIfUnowned(key);
    }

    public void releaseAll(String owner) {
        requireOwner(owner);

        String scopePrefix = owner + "::";
        Set<String> owners = new HashSet<>();
        owners.add(owner);
        collectOwners(owners, pressedByScript, scopePrefix);
        collectOwners(owners, blockedByScript, scopePrefix);
        collectOwners(owners, watchedByScript, scopePrefix);
        for (Tap tap : activeTaps.values()) {
            if (tap.scriptName().equals(owner) || tap.scriptName().startsWith(scopePrefix)) {
                owners.add(tap.scriptName());
            }
        }
        owners.forEach(this::releaseOwnedInputs);
    }

    private void releaseOwnedInputs(String owner) {
        Set<InputConstants.Key> affected = new HashSet<>();
        collectAndRemove(pressedByScript, owner, affected);
        for (Map.Entry<Long, Tap> entry : activeTaps.entrySet()) {
            Tap tap = entry.getValue();
            if (tap.scriptName().equals(owner) && activeTaps.remove(entry.getKey(), tap)) {
                affected.add(tap.key());
            }
        }

        Set<InputConstants.Key> blocked = new HashSet<>();
        collectAndRemove(blockedByScript, owner, blocked);
        Set<InputConstants.Key> watched = watchedByScript.remove(owner);
        if (watched != null) {
            blocked.addAll(watched);
        }
        for (InputConstants.Key key : affected) {
            releaseIfUnowned(key);
        }
        for (InputConstants.Key key : blocked) {
            if (!isBlocked(key)) {
                blockedSet(key).remove(key.getValue());
                restorePhysicalState(key);
            }
        }
    }

    public void removeScript(String scriptName) {
        releaseAll(scriptName);
    }

    public boolean isDown(InputConstants.Key key) {
        requireKey(key);
        return isReallyDown(key) || isPressedByAnyScript(key) || hasActiveTap(key);
    }

    public void block(String owner, InputConstants.Key key) {
        requireOwner(owner);
        requireKey(key);
        if (track(blockedByScript, owner).add(key)) {
            blockedSet(key).add(key.getValue());
            fireRaw(key, GLFW.GLFW_RELEASE);
        }
    }

    public void blockFor(String owner, InputConstants.Key key, long durationMs) {
        requireOwner(owner);
        requireKey(key);
        String leaseOwner = owner + "::blockFor:" + inputIds.incrementAndGet();
        block(leaseOwner, key);
        Minecraft client = Minecraft.getInstance();
        SCHEDULER.schedule(
                () -> client.execute(() -> unblock(leaseOwner, key)),
                Math.max(0L, durationMs),
                TimeUnit.MILLISECONDS);
    }

    public void unblock(String owner, InputConstants.Key key) {
        requireOwner(owner);
        requireKey(key);
        Set<InputConstants.Key> ownedBlocks = blockedByScript.get(owner);
        if (ownedBlocks == null || !ownedBlocks.contains(key)) {
            return;
        }
        removeTrackedKey(blockedByScript, owner, key);
        removeTrackedKey(watchedByScript, owner, key);
        updateBlockedState(key);
    }

    public void blockUntilRelease(String owner, InputConstants.Key key) {
        requireOwner(owner);
        requireKey(key);
        track(blockedByScript, owner).add(key);
        track(watchedByScript, owner).add(key);
        blockedSet(key).add(key.getValue());
        fireRaw(key, GLFW.GLFW_RELEASE);
    }

    public void lockCursor(double x, double y) {
        lockedCursorX = x;
        lockedCursorY = y;
        cursorLocked = true;

        Minecraft client = Minecraft.getInstance();

        Runnable moveCursor = () -> {
            if (cursorLocked) {
                GLFW.glfwSetCursorPos(
                        client.getWindow().handle(),
                        lockedCursorX,
                        lockedCursorY);
            }
        };

        if (client.isSameThread()) {
            moveCursor.run();
        } else {
            client.execute(moveCursor);
        }
    }

    public void unlockCursor() {
        cursorLocked = false;
    }

    public boolean shouldBlock(InputConstants.Key key) {
        return !firingSynthetic.get() && isBlocked(key);
    }

    public boolean isReallyDown(InputConstants.Key key) {
        requireKey(key);
        Minecraft client = Minecraft.getInstance();
        long window = client.getWindow().handle();
        boolean wasSynthetic = firingSynthetic.get();

        try {
            firingSynthetic.set(true);
            if (key.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        } finally {
            firingSynthetic.set(wasSynthetic);
        }
    }

    private void pollReleaseWatchers() {
        for (Map.Entry<String, Set<InputConstants.Key>> entry : watchedByScript.entrySet()) {
            String owner = entry.getKey();
            Set<InputConstants.Key> watched = entry.getValue();
            for (InputConstants.Key key : new HashSet<>(watched)) {
                if (!isReallyDown(key)) {
                    watched.remove(key);
                    removeTrackedKey(blockedByScript, owner, key);
                    updateBlockedState(key);
                }
            }
            if (watched.isEmpty()) {
                watchedByScript.remove(owner, watched);
            }
        }
    }

    private void updateBlockedState(InputConstants.Key key) {
        if (isBlocked(key)) {
            blockedSet(key).add(key.getValue());
            return;
        }
        blockedSet(key).remove(key.getValue());
        restorePhysicalState(key);
    }

    private void releaseIfUnowned(InputConstants.Key key) {
        if (isPressedByAnyScript(key) || hasActiveTap(key)) {
            return;
        }
        fireRaw(key, GLFW.GLFW_RELEASE);
        if (restoreAfterTaps.remove(key)) {
            restorePhysicalState(key);
        }
    }

    private boolean isBlocked(InputConstants.Key key) {
        return blockedByScript.values().stream().anyMatch(keys -> keys.contains(key));
    }

    private boolean isPressedByAnyScript(InputConstants.Key key) {
        return pressedByScript.values().stream().anyMatch(keys -> keys.contains(key));
    }

    @SuppressWarnings("null")
    private boolean hasActiveTap(InputConstants.Key key) {
        return activeTaps.values().stream().anyMatch(tap -> tap.key().equals(key));
    }

    private Set<Integer> blockedSet(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.MOUSE ? blockedMouseButtons : blockedKeys;
    }

    private void restorePhysicalState(InputConstants.Key key) {
        if (isReallyDown(key) && !isBlocked(key)) {
            fireRaw(key, GLFW.GLFW_PRESS);
        }
    }

    private void fireRaw(InputConstants.Key key, int action) {
        Minecraft client = Minecraft.getInstance();
        long window = client.getWindow().handle();
        boolean wasSynthetic = firingSynthetic.get();
        try {
            firingSynthetic.set(true);
            if (key.getType() == InputConstants.Type.MOUSE) {
                GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(window, null);
                if (callback != null) {
                    GLFW.glfwSetMouseButtonCallback(window, callback);
                    callback.invoke(window, key.getValue(), action, 0);
                }
            } else {
                GLFWKeyCallback callback = GLFW.glfwSetKeyCallback(window, null);
                if (callback != null) {
                    GLFW.glfwSetKeyCallback(window, callback);
                    callback.invoke(window, key.getValue(), 0, action, 0);
                }
            }
        } finally {
            firingSynthetic.set(wasSynthetic);
        }
    }

    private Set<InputConstants.Key> track(
            ConcurrentMap<String, Set<InputConstants.Key>> map,
            String owner) {
        return map.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet());
    }

    private void removeTrackedKey(
            ConcurrentMap<String, Set<InputConstants.Key>> map,
            String owner,
            InputConstants.Key key) {
        Set<InputConstants.Key> keys = map.get(owner);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                map.remove(owner, keys);
            }
        }
    }

    private void collectAndRemove(
            ConcurrentMap<String, Set<InputConstants.Key>> map,
            String owner,
            Set<InputConstants.Key> result) {
        Set<InputConstants.Key> keys = map.remove(owner);
        if (keys != null) {
            result.addAll(keys);
        }
    }

    private void collectOwners(
            Set<String> owners,
            ConcurrentMap<String, Set<InputConstants.Key>> map,
            String prefix) {
        for (String owner : map.keySet()) {
            if (owner.startsWith(prefix)) {
                owners.add(owner);
            }
        }
    }

    private void requireKey(InputConstants.Key key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
    }

    private void requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Input owner cannot be null or blank");
        }
    }
}
