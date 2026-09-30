package com.konqasasas.localreplay.replay.camera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;

/**
 * Client-only render-view entity. It is deliberately never added to the world.
 *
 * ReplayTick stores the player's feet position (EntityPlayerSP#posY), so this
 * camera intentionally behaves like a standing 1.12.2 player and lets vanilla
 * add the normal 1.62 block eye height exactly once. Keeping the previous,
 * current and last-tick transforms identical is important because Local Replay
 * already performs its own sub-tick interpolation.
 */
final class ReplayCameraEntity extends EntityOtherPlayerMP {

    private static final float REPLAY_EYE_HEIGHT = 1.62F;

    ReplayCameraEntity(WorldClient world, GameProfile profile) {
        super(world, profile);
        this.noClip = true;
        this.setInvisible(true);
        this.setSize(0.6F, 1.8F);

        // A replay camera must never inherit walking/bobbing state from the
        // player-shaped base class.
        this.distanceWalkedModified = 0.0F;
        this.prevDistanceWalkedModified = 0.0F;
        this.cameraYaw = 0.0F;
        this.prevCameraYaw = 0.0F;
        this.cameraPitch = 0.0F;
        this.prevCameraPitch = 0.0F;
    }

    /**
     * EntityRenderer#orientCamera() asks the render-view entity for its eye
     * height. Make this explicit instead of depending on player state that is
     * not part of ReplayTick (sleeping/sneaking/elytra etc.).
     */
    @Override
    public float getEyeHeight() {
        return REPLAY_EYE_HEIGHT;
    }

    /**
     * RenderGlobal's sky/fog calculations also query the eye position directly
     * in several 1.12.2 paths. Return the same view position used by
     * EntityRenderer so terrain, fog and the lower-sky horizon agree.
     */
    @Override
    public Vec3d getPositionEyes(float partialTicks) {
        double x = this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks;
        double y = this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks
                + (double) REPLAY_EYE_HEIGHT;
        double z = this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks;
        return new Vec3d(x, y, z);
    }

    void applyPose(double x, double feetY, double z, float yaw, float pitch) {
        // setPosition updates the bounding box as well as pos*, which matters to
        // vanilla camera/sky code even though this entity is never in the world.
        this.setPosition(x, feetY, z);

        // The playback system already performs interpolation. Keep previous and
        // current values identical so vanilla does not apply a second lerp.
        this.prevPosX = x;
        this.prevPosY = feetY;
        this.prevPosZ = z;
        this.lastTickPosX = x;
        this.lastTickPosY = feetY;
        this.lastTickPosZ = z;

        this.rotationYaw = yaw;
        this.prevRotationYaw = yaw;
        this.rotationYawHead = yaw;
        this.prevRotationYawHead = yaw;
        this.renderYawOffset = yaw;
        this.prevRenderYawOffset = yaw;

        this.rotationPitch = pitch;
        this.prevRotationPitch = pitch;

        // Keep all first-person camera bobbing state neutral. The camera should
        // represent the recorded XYZ/Yaw/Pitch only.
        this.distanceWalkedModified = 0.0F;
        this.prevDistanceWalkedModified = 0.0F;
        this.cameraYaw = 0.0F;
        this.prevCameraYaw = 0.0F;
        this.cameraPitch = 0.0F;
        this.prevCameraPitch = 0.0F;

        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.fallDistance = 0.0F;
    }
}
