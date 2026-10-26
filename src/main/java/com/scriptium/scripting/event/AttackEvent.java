package com.scriptium.scripting.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class AttackEvent {

    private final Player player;
    private final Entity target;

    public AttackEvent(Player player, Entity target) {
        this.player = player;
        this.target = target;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getTarget() {
        return target;
    }
}
