package com.scriptium.scripting.event;

import net.minecraft.client.DeltaTracker;

public final class RenderEvent {

    private final DeltaTracker deltaTracker;

    public RenderEvent(DeltaTracker deltaTracker) {
        this.deltaTracker = deltaTracker;
    }

    public DeltaTracker getDeltaTracker() {
        return deltaTracker;
    }
}
