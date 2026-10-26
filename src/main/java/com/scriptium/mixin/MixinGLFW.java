package com.scriptium.mixin;

import com.scriptium.scripting.input.InputManager;

import org.lwjgl.glfw.GLFW;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GLFW.class, remap = false)
public abstract class MixinGLFW {

    @Inject(method = "glfwGetKey", at = @At("HEAD"), cancellable = true, remap = false)
    private static void scriptium$blockKeyPolling(
            long window,
            int keyCode,
            CallbackInfoReturnable<Integer> cir) {
        if (InputManager.shouldBlockKey(keyCode)) {
            cir.setReturnValue(GLFW.GLFW_RELEASE);
        }
    }

    @Inject(method = "glfwGetMouseButton", at = @At("HEAD"), cancellable = true, remap = false)
    private static void scriptium$blockMousePolling(
            long window,
            int button,
            CallbackInfoReturnable<Integer> cir) {
        if (InputManager.shouldBlockMouse(button)) {
            cir.setReturnValue(GLFW.GLFW_RELEASE);
        }
    }
}
