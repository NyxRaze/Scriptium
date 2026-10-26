package com.scriptium.scripting.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;

public final class StabEvent {

    private final Player player;
    private final PiercingWeapon weapon;

    public StabEvent(Player player, PiercingWeapon weapon) {
        this.player = player;
        this.weapon = weapon;
    }

    public Player getPlayer() {
        return player;
    }

    public PiercingWeapon getWeapon() {
        return weapon;
    }
}
