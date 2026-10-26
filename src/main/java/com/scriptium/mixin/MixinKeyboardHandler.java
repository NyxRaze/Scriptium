package com.scriptium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.scriptium.scripting.input.InputManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

    @SuppressWarnings("null")
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void scriptium$blockKeyboardInput(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (InputManager.shouldBlockGlobal(InputConstants.getKey(event))) {
            ci.cancel();
        }
    }
}
