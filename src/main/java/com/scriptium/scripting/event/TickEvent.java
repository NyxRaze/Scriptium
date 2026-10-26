package com.scriptium.scripting.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class TickEvent {

    private final Minecraft mc;

    public TickEvent(Minecraft mc) {
        this.mc = mc;
    }

    public Minecraft getMc() {
        return mc;
    }

    public LocalPlayer getPlayer() {
        return mc.player;
    }
}