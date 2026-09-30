package com.konqasasas.localreplay.replay.camera;

import com.konqasasas.localreplay.replay.playback.ReplayPose;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Owns the temporary render-view entity used by replay playback.
 * No entity is spawned or registered with the world or server.
 */
public final class ReplayCameraManager {

    private static final UUID CAMERA_UUID = UUID.nameUUIDFromBytes(
            "localreplay-camera".getBytes(StandardCharsets.UTF_8)
    );

    private final Minecraft minecraft;
    private ReplayCameraEntity camera;
    private WorldClient cameraWorld;
    private int previousThirdPersonView;
    private boolean perspectiveCaptured;

    public ReplayCameraManager(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft cannot be null");
        }
        this.minecraft = minecraft;
    }

    public void activate(WorldClient world, ReplayPose initialPose) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        if (initialPose == null) {
            throw new IllegalArgumentException("initialPose cannot be null");
        }

        deactivate();

        GameProfile profile = new GameProfile(CAMERA_UUID, "LocalReplayCamera");
        camera = new ReplayCameraEntity(world, profile);
        cameraWorld = world;
        previousThirdPersonView = minecraft.gameSettings.thirdPersonView;
        perspectiveCaptured = true;
        minecraft.gameSettings.thirdPersonView = 0;

        // Fully initialize all camera transforms before it becomes the render
        // view entity. This avoids a single frame that mixes constructor/default
        // coordinates with the replay position (notably visible in sky/fog).
        updatePose(initialPose);
        minecraft.setRenderViewEntity(camera);
    }

    public void updatePose(ReplayPose pose) {
        if (camera == null || pose == null) {
            return;
        }
        camera.applyPose(
                pose.getX(), pose.getY(), pose.getZ(),
                pose.getYaw(), pose.getPitch()
        );
    }

    public boolean isActive() {
        return camera != null;
    }

    public boolean isValidFor(WorldClient world) {
        return camera != null && cameraWorld == world;
    }

    public void deactivate() {
        if (camera == null) {
            return;
        }

        Entity currentView = minecraft.getRenderViewEntity();
        if (currentView == camera) {
            minecraft.setRenderViewEntity(minecraft.player);
        }

        if (perspectiveCaptured) {
            minecraft.gameSettings.thirdPersonView = previousThirdPersonView;
            perspectiveCaptured = false;
        }

        camera = null;
        cameraWorld = null;
    }
}
