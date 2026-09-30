package com.konqasasas.localreplay.replay.data;

/**
 * One Minecraft client-tick sample used by Local Replay.
 *
 * This intentionally contains only generic player state. Compatibility layers
 * for CYV/MPK/other HUD mods must derive their values from these fields rather
 * than storing mod-specific runtime state in the replay.
 */
public final class ReplayTick {

    public static final int INPUT_FORWARD = 1 << 0;
    public static final int INPUT_LEFT = 1 << 1;
    public static final int INPUT_BACK = 1 << 2;
    public static final int INPUT_RIGHT = 1 << 3;
    public static final int INPUT_JUMP = 1 << 4;
    public static final int INPUT_SPRINT = 1 << 5;
    public static final int INPUT_SNEAK = 1 << 6;

    public static final int ALL_INPUT_MASK = INPUT_FORWARD
            | INPUT_LEFT
            | INPUT_BACK
            | INPUT_RIGHT
            | INPUT_JUMP
            | INPUT_SPRINT
            | INPUT_SNEAK;

    public static final int STATE_ON_GROUND = 1 << 0;
    public static final int STATE_FLYING = 1 << 1;
    public static final int STATE_SPRINTING = 1 << 2;

    /** Bits 3..7 are intentionally reserved for future generic player state. */
    public static final int ALL_STATE_MASK = STATE_ON_GROUND
            | STATE_FLYING
            | STATE_SPRINTING;

    private final double x;
    private final double y;
    private final double z;

    private final double motionX;
    private final double motionY;
    private final double motionZ;

    private final float yaw;
    private final float pitch;

    private final byte inputFlags;
    private final byte stateFlags;

    /**
     * Current format constructor.
     */
    public ReplayTick(
            double x,
            double y,
            double z,
            double motionX,
            double motionY,
            double motionZ,
            float yaw,
            float pitch,
            byte inputFlags,
            byte stateFlags
    ) {
        validateFinite(x, "x");
        validateFinite(y, "y");
        validateFinite(z, "z");
        validateFinite(motionX, "motionX");
        validateFinite(motionY, "motionY");
        validateFinite(motionZ, "motionZ");
        validateFinite(yaw, "yaw");
        validateFinite(pitch, "pitch");

        int unsignedInputFlags = inputFlags & 0xFF;
        if ((unsignedInputFlags & ~ALL_INPUT_MASK) != 0) {
            throw new IllegalArgumentException(
                    "inputFlags contains unsupported bits: " + unsignedInputFlags
            );
        }

        int unsignedStateFlags = stateFlags & 0xFF;
        if ((unsignedStateFlags & ~ALL_STATE_MASK) != 0) {
            throw new IllegalArgumentException(
                    "stateFlags contains unsupported bits: " + unsignedStateFlags
            );
        }

        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.inputFlags = inputFlags;
        this.stateFlags = stateFlags;
    }

    /**
     * Legacy in-memory constructor retained for source compatibility with v1
     * callers. New recording code must use the full constructor above.
     *
     * Legacy in-memory samples have no motion/state information, so those
     * fields default to zero. The .lreplay v1 loader performs a better upgrade
     * by deriving motion from adjacent recorded positions.
     */
    public ReplayTick(double x, double y, double z, float yaw, float pitch, byte inputFlags) {
        this(x, y, z, 0.0D, 0.0D, 0.0D, yaw, pitch, inputFlags, (byte) 0);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getMotionX() {
        return motionX;
    }

    public double getMotionY() {
        return motionY;
    }

    public double getMotionZ() {
        return motionZ;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public byte getInputFlags() {
        return inputFlags;
    }

    public byte getStateFlags() {
        return stateFlags;
    }

    public boolean isForwardDown() {
        return hasInput(INPUT_FORWARD);
    }

    public boolean isLeftDown() {
        return hasInput(INPUT_LEFT);
    }

    public boolean isBackDown() {
        return hasInput(INPUT_BACK);
    }

    public boolean isRightDown() {
        return hasInput(INPUT_RIGHT);
    }

    public boolean isJumpDown() {
        return hasInput(INPUT_JUMP);
    }

    public boolean isSprintDown() {
        return hasInput(INPUT_SPRINT);
    }

    public boolean isSneakDown() {
        return hasInput(INPUT_SNEAK);
    }

    public boolean isOnGround() {
        return hasState(STATE_ON_GROUND);
    }

    public boolean isFlying() {
        return hasState(STATE_FLYING);
    }

    public boolean isSprinting() {
        return hasState(STATE_SPRINTING);
    }

    public boolean hasInput(int inputMask) {
        return ((inputFlags & 0xFF) & inputMask) != 0;
    }

    public boolean hasState(int stateMask) {
        return ((stateFlags & 0xFF) & stateMask) != 0;
    }

    public static byte buildInputFlags(
            boolean forward,
            boolean left,
            boolean back,
            boolean right,
            boolean jump,
            boolean sprint,
            boolean sneak
    ) {
        int flags = 0;
        if (forward) flags |= INPUT_FORWARD;
        if (left) flags |= INPUT_LEFT;
        if (back) flags |= INPUT_BACK;
        if (right) flags |= INPUT_RIGHT;
        if (jump) flags |= INPUT_JUMP;
        if (sprint) flags |= INPUT_SPRINT;
        if (sneak) flags |= INPUT_SNEAK;
        return (byte) flags;
    }

    public static byte buildStateFlags(boolean onGround, boolean flying, boolean sprinting) {
        int flags = 0;
        if (onGround) flags |= STATE_ON_GROUND;
        if (flying) flags |= STATE_FLYING;
        if (sprinting) flags |= STATE_SPRINTING;
        return (byte) flags;
    }

    private static void validateFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void validateFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
