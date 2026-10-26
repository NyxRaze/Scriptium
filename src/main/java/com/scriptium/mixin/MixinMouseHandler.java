package com.scriptium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.scriptium.scripting.aim.AimController;
import com.scriptium.scripting.input.InputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @SuppressWarnings("null")
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void scriptium$applyAimDeltasAndLock(double deltaTime, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }

        boolean isScoping = mc.options.getCameraType().isFirstPerson() && mc.player.isScoping();
        AimController controller = AimController.getInstance();
        double[] deltas = controller.pullDeltas(mc.player, isScoping, deltaTime);

        if (controller.isLocked()) {
            mc.player.turn(deltas[0] / 0.15, deltas[1] / 0.15);
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;
            controller.clearAimRate();
            ci.cancel();
        } else {
            accumulatedDX += deltas[0];
            accumulatedDY += deltas[1];
        }
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void scriptium$clearAimRate(double deltaTime, CallbackInfo ci) {
        AimController.getInstance().clearAimRate();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void scriptium$blockMouseInput(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(buttonInfo.button());
        if (InputManager.shouldBlockGlobal(key)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void scriptium$lockCursor(
            long handle,
            double xpos,
            double ypos,
            CallbackInfo ci) {

        if (handle != this.minecraft.getWindow().handle()) {
            return;
        }

        if (!InputManager.isCursorLocked()) {
            return;
        }

        double targetX = InputManager.getLockedCursorX();
        double targetY = InputManager.getLockedCursorY();

        // Discard physical mouse movement.
        this.xpos = targetX;
        this.ypos = targetY;
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;

        // Restore the OS cursor position if it moved.
        if (xpos != targetX || ypos != targetY) {
            GLFW.glfwSetCursorPos(handle, targetX, targetY);
        }

        ci.cancel();
    }
}
