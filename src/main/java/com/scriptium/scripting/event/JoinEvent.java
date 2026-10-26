package com.scriptium.scripting.event;

import net.minecraft.client.player.LocalPlayer;

public final class JoinEvent {

    private final LocalPlayer player;

    public JoinEvent(LocalPlayer player) {
        this.player = player;
    }

    public LocalPlayer getPlayer() {
        return player;
    }
}