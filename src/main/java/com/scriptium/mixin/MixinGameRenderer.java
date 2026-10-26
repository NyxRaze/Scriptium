package com.scriptium.mixin;

import com.scriptium.scripting.event.RenderEvent;
import com.scriptium.scripting.event.ScriptEventBus;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "render", at = @At("TAIL"))
    private void scriptium$onRenderTail(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        ScriptEventBus.emit("render", new RenderEvent(deltaTracker));
    }
}
