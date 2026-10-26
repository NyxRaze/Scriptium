package com.scriptium.scripting.event;

import net.minecraft.client.player.LocalPlayer;

public final class LeaveEvent {

    private final LocalPlayer player;

    public LeaveEvent(LocalPlayer player) {
        this.player = player;
    }

    public LocalPlayer getPlayer() {
        return player;
    }
}