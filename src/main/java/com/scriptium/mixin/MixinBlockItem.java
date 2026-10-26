package com.scriptium.mixin;

import com.scriptium.scripting.event.ScriptEventBus;
import com.scriptium.scripting.event.PlaceEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Unique
    private static final ThreadLocal<PlacementData> scriptium$placement = new ThreadLocal<>();

    @Unique
    private static final class PlacementData {
        final BlockPos pos;
        final Player player;
        final Level level;

        PlacementData(BlockPos pos, Player player, Level level) {
            this.pos = pos.immutable();
            this.player = player;
            this.level = level;
        }
    }

    @Inject(method = "placeBlock", at = @At("HEAD"))
    private void scriptium$capturePlacement(
            BlockPlaceContext context,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir) {

        scriptium$placement.set(new PlacementData(
                context.getClickedPos(),
                context.getPlayer(),
                context.getLevel()));
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void scriptium$firePlaceEvent(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir) {

        PlacementData data = scriptium$placement.get();
        scriptium$placement.remove();

        if (data == null) {
            return;
        }

        if (cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        if (data.player == null || data.level.isClientSide() == false) {
            return;
        }

        if (!data.player.isLocalPlayer()) {
            return;
        }

        ScriptEventBus.emit(
                "place",
                new PlaceEvent(data.player, data.pos));
    }
}