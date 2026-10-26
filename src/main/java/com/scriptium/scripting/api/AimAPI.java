package com.scriptium.scripting.api;

import com.scriptium.scripting.aim.AimController;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class AimAPI {

    private static final Minecraft MC = Minecraft.getInstance();

    private final String scriptName;
    private final AimController controller;

    public AimAPI(String scriptName) {
        this.scriptName = scriptName;
        this.controller = AimController.getInstance();
    }

    public void setLockDurationSeconds(double seconds) {
        controller.setLockDurationSeconds(seconds);
    }

    public boolean lockToPosition(double targetYaw, double targetPitch) {
        return controller.lockToPosition(scriptName, targetYaw, targetPitch);
    }

    public boolean lockToPosition(double targetYaw, double targetPitch, double durationSeconds) {
        return controller.lockToPosition(scriptName, targetYaw, targetPitch, durationSeconds);
    }

    public boolean lockToPositionInstant(double targetYaw, double targetPitch) {
        return controller.lockToPosition(scriptName, targetYaw, targetPitch, 0.0);
    }

    public boolean isLockComplete() {
        return controller.isLockedBy(scriptName) && controller.isLockComplete();
    }

    public void setAimMultiplier(double multiplier) {
        controller.setAimMultiplier(multiplier);
    }

    public void addAimDelta(double yaw, double pitch) {
        controller.addAimDelta(yaw, pitch);
    }

    public void clearAimRate() {
        controller.clearAimRate();
    }

    public boolean isLocked() {
        return controller.isLocked();
    }

    public boolean isLockedByScript() {
        return controller.isLockedBy(scriptName);
    }

    public void forceUnlock() {
        controller.forceUnlock(scriptName);
    }

    public void globalUnlock() {
        controller.globalUnlock();
    }

    public float[] getRotationsToVector(Vec3 target) {
        if (target == null) {
            throw new IllegalArgumentException("Aim target vector cannot be null.");
        }
        if (MC.player == null) {
            return new float[] { 0.0f, 0.0f };
        }

        float partialTick = MC.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        @SuppressWarnings("null")
        double playerX = Mth.lerp((double) partialTick, MC.player.xo, MC.player.getX());
        @SuppressWarnings("null")
        double playerY = Mth.lerp((double) partialTick, MC.player.yo, MC.player.getY());
        @SuppressWarnings("null")
        double playerZ = Mth.lerp((double) partialTick, MC.player.zo, MC.player.getZ());
        @SuppressWarnings("null")
        double eyeHeight = MC.player.getEyeHeight(MC.player.getPose());
        double dx = target.x - playerX;
        double dy = target.y - (playerY + eyeHeight);
        double dz = target.z - playerZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        return new float[] {
                Mth.wrapDegrees(yaw),
                Mth.wrapDegrees(pitch)
        };
    }

    public float[] getRotationsToBlock(BlockPos pos) {
        return getRotationsToBlock(pos, 0.5);
    }

    public float[] getRotationsToBlock(BlockPos pos, double yOffset) {
        if (pos == null) {
            throw new IllegalArgumentException("Aim target block cannot be null.");
        }
        return getRotationsToVector(new Vec3(
                pos.getX() + 0.5,
                pos.getY() + yOffset,
                pos.getZ() + 0.5));
    }

    public float[] getRotationsToEntity(Entity entity, double heightFactor) {
        if (entity == null) {
            throw new IllegalArgumentException("Aim target entity cannot be null.");
        }
        if (!Double.isFinite(heightFactor)) {
            throw new IllegalArgumentException("Aim target height factor must be a finite number.");
        }
        Vec3 target = entity.position().add(0.0, entity.getEyeHeight() * heightFactor, 0.0);
        return getRotationsToVector(target);
    }

    public double getAngleDifference(Vec3 target) {
        if (target == null) {
            throw new IllegalArgumentException("Aim target vector cannot be null.");
        }
        if (MC.player == null) {
            return 0.0;
        }

        float[] rotations = getRotationsToVector(target);
        @SuppressWarnings("null")
        double yawDiff = Mth.wrapDegrees(rotations[0] - MC.player.getYRot());
        @SuppressWarnings("null")
        double pitchDiff = Mth.wrapDegrees(rotations[1] - MC.player.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
}
