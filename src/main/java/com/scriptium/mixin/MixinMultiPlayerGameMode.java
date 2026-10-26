package com.scriptium.mixin;

import com.scriptium.scripting.event.ScriptEventBus;
import com.scriptium.scripting.event.AttackEvent;
import com.scriptium.scripting.event.StabEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "attack", at = @At("HEAD"))
    private void scriptium$fireAttackEvent(Player player, Entity target, CallbackInfo ci) {
        if (player != null) {
            ScriptEventBus.emit("attack", new AttackEvent(player, target));
        }
    }

    @Inject(method = "piercingAttack", at = @At("TAIL"))
    private void scriptium$firePiercingEvent(PiercingWeapon weapon, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ScriptEventBus.emit("stab", new StabEvent(player, weapon));
        }
    }
}
