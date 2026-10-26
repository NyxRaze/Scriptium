package com.scriptium.scripting.aim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class AimController {

    private static final AimController INSTANCE = new AimController();

    private static final Minecraft MC = Minecraft.getInstance();

    /** Retarget threshold (degrees) for restarting a finished lock's timer. */
    private static final double RETARGET_EPSILON = 0.01;

    private double lockDurationSeconds = 0.25;
    private double aimMultiplier = 1.0;
    private double accumulatedYawDelta;
    private double accumulatedPitchDelta;

    private String lockOwner;
    private double lockYaw;
    private double lockPitch;
    private double lockRemainingSeconds;

    private AimController() {
    }

    public static AimController getInstance() {
        return INSTANCE;
    }

    /** Default duration used by lockToPosition(owner, yaw, pitch). */
    public void setLockDurationSeconds(double seconds) {
        requireFinite(seconds, "Lock duration");
        lockDurationSeconds = Math.max(0.0, seconds);
    }

    public void setAimMultiplier(double multiplier) {
        requireFinite(multiplier, "Aim multiplier");
        aimMultiplier = Math.max(0.0, multiplier);
    }

    public void addAimDelta(double yaw, double pitch) {
        requireFinite(yaw, "Yaw delta");
        requireFinite(pitch, "Pitch delta");

        if (isLocked()) {
            return;
        }

        accumulatedYawDelta += yaw * aimMultiplier;
        accumulatedPitchDelta += pitch * aimMultiplier;
    }

    public boolean lockToPosition(String owner, double targetYaw, double targetPitch) {
        return lockToPosition(owner, targetYaw, targetPitch, lockDurationSeconds);
    }

    /**
     * Aims at the target so that the movement completes in durationSeconds.
     * A duration of 0 snaps instantly.
     *
     * If this owner already has a movement in progress, the target is updated
     * but the original deadline is kept (so calling this every tick while
     * tracking a target doesn't keep resetting the timer). The timer restarts
     * when the lock is new, when the previous movement has finished and the
     * target changed, or when a duration of 0 is requested.
     */
    public boolean lockToPosition(
            String owner,
            double targetYaw,
            double targetPitch,
            double durationSeconds) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Lock owner must be non-blank.");
        }
        requireFinite(targetYaw, "Target yaw");
        requireFinite(targetPitch, "Target pitch");
        requireFinite(durationSeconds, "Lock duration");

        if (lockOwner != null && !lockOwner.equals(owner)) {
            return false;
        }

        double duration = Math.max(0.0, durationSeconds);

        boolean wasLocked = lockOwner != null;
        boolean finished = wasLocked && lockRemainingSeconds <= 0.0;
        boolean targetChanged = Math.abs(Mth.wrapDegrees(targetYaw - lockYaw)) > RETARGET_EPSILON
                || Math.abs(targetPitch - lockPitch) > RETARGET_EPSILON;

        if (!wasLocked || (finished && targetChanged)) {
            lockRemainingSeconds = duration;
        } else if (duration <= 0.0) {
            lockRemainingSeconds = 0.0;
        }

        lockOwner = owner;
        lockYaw = targetYaw;
        lockPitch = targetPitch;
        clearAimRate();
        return true;
    }

    public boolean isLocked() {
        return lockOwner != null;
    }

    public boolean isLockedBy(String owner) {
        return lockOwner != null && lockOwner.equals(owner);
    }

    public double[] pullDeltas(LocalPlayer player, boolean isScoping, double deltaTime) {
        double[] deltas = new double[2];

        if (isLocked()) {
            double yawDiff = Mth.wrapDegrees(lockYaw - player.getYRot());
            double pitchDiff = lockPitch - player.getXRot();

            if (lockRemainingSeconds <= 0.0 || deltaTime >= lockRemainingSeconds) {
                // Final step (or already finished): land exactly on target.
                deltas[0] = yawDiff;
                deltas[1] = pitchDiff;
                lockRemainingSeconds = 0.0;
            } else {
                double fraction = deltaTime / lockRemainingSeconds;
                deltas[0] = yawDiff * fraction;
                deltas[1] = pitchDiff * fraction;
                lockRemainingSeconds -= deltaTime;
            }
        } else {
            double sensitivity = MC.options.sensitivity().get();
            double s = (sensitivity * 0.6) + 0.2;
            double modifier = s * s * s;
            double g = isScoping ? modifier : modifier * 8.0;

            deltas[0] = accumulatedYawDelta / (g * 0.15);
            deltas[1] = accumulatedPitchDelta / (g * 0.15);
        }

        return deltas;
    }

    /** True once a lock's movement has reached its target. */
    public boolean isLockComplete() {
        return lockOwner != null && lockRemainingSeconds <= 0.0;
    }

    public void clearAimRate() {
        accumulatedYawDelta = 0.0;
        accumulatedPitchDelta = 0.0;
    }

    public void forceUnlock(String owner) {
        if (isLockedBy(owner)) {
            lockOwner = null;
            lockRemainingSeconds = 0.0;
        }
    }

    public void releaseOwner(String owner) {
        forceUnlock(owner);
    }

    public void globalUnlock() {
        lockOwner = null;
        lockRemainingSeconds = 0.0;
        clearAimRate();
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be a finite number.");
        }
    }
}