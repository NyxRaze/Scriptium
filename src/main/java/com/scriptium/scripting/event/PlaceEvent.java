package com.scriptium.scripting.event;

import net.minecraft.world.entity.player.Player;

import net.minecraft.core.BlockPos;

public final class PlaceEvent {

    private final Player player;
    private final BlockPos candidatePos;

    public PlaceEvent(Player player, BlockPos candidatePos) {
        this.player = player;
        this.candidatePos = candidatePos;
    }

    public Player getPlayer() {
        return player;
    }

    public BlockPos getCandidatePos() {
        return candidatePos;
    }
}
