package com.konqasasas.localreplay.replay.hud;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.hud.cyv.CyvHudAdapter;
import com.konqasasas.localreplay.replay.hud.delay.DelayHudAdapter;
import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.replay.hud.mpk.MpkModVariant;
import com.konqasasas.localreplay.replay.hud.mpk1.Mpk1HudAdapter;
import com.konqasasas.localreplay.replay.hud.mpk2.Mpk2HudAdapter;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;

/**
 * Central compatibility-HUD compositor for Local Replay.
 *
 * Every installed/supported adapter may be active at the same time. Local
 * Replay deliberately does not choose a "winner" when, for example, CYV and
 * MPK Mod are both installed; it reproduces both configured HUDs just like the
 * normal game would.
 */
public final class ReplayHudManager {

    private final Minecraft minecraft;
    private final Logger logger;
    private final List<ReplayHudAdapter> adapters;

    public ReplayHudManager(Minecraft minecraft, ReplayData data, Logger logger) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (data == null) throw new IllegalArgumentException("data cannot be null");

        this.minecraft = minecraft;
        this.logger = logger;

        ReplayHudTimeline timeline = new ReplayHudTimeline(data);
        List<ReplayHudAdapter> registered = new ArrayList<ReplayHudAdapter>();

        // Stable draw order. If users intentionally place HUDs on top of each
        // other, later adapters naturally appear above earlier adapters.
        registered.add(new CyvHudAdapter(minecraft, timeline, logger));

        // MPK Mod 1.x and MPKMod2 deliberately share the same Forge mod id.
        // Detect the installed generation once per replay so a stale config
        // from the other generation cannot make both MPK adapters appear.
        Path minecraftDirectory = null;
        if (LocalReplayMod.getPaths() != null) {
            minecraftDirectory = LocalReplayMod.getPaths().getRootDirectory().getParent();
        }
        MpkModVariant mpkVariant = MpkModVariant.detect(minecraftDirectory);
        if (mpkVariant == MpkModVariant.MPK1) {
            registered.add(new Mpk1HudAdapter(minecraft, data, timeline, logger));
        } else if (mpkVariant == MpkModVariant.MPK2) {
            registered.add(new Mpk2HudAdapter(minecraft, data, timeline, logger));
        }

        // Delay Mod is independent of CYV/MPK. It uses only its read-only
        // config snapshot and replay onGround history, and remains visible
        // during F3 just like the source mod.
        registered.add(new DelayHudAdapter(minecraft, data, logger));

        this.adapters = Collections.unmodifiableList(registered);

        if (logger != null) {
            StringBuilder active = new StringBuilder();
            for (ReplayHudAdapter adapter : adapters) {
                if (!adapter.isActive()) continue;
                if (active.length() > 0) active.append(", ");
                active.append(adapter.getId());
            }
            logger.info("Replay HUD adapters active: {}", active.length() == 0 ? "none" : active.toString());
        }
    }

    public boolean hasActiveAdapters() {
        for (ReplayHudAdapter adapter : adapters) {
            if (adapter.isActive()) return true;
        }
        return false;
    }

    public void render(
            ReplayPlayback playback,
            int x,
            int y,
            int width,
            int height,
            float partialTicks
    ) {
        if (playback == null || width <= 0 || height <= 0 || !hasActiveAdapters()) return;

        ScaledResolution sr = new ScaledResolution(minecraft);
        int sourceWidth = Math.max(1, sr.getScaledWidth());
        int sourceHeight = Math.max(1, sr.getScaledHeight());

        // Full-screen replay mode intentionally uses Minecraft's normal GUI
        // coordinate space with no fit/scale transform. This keeps CYV/MPK
        // positions pixel-aligned with normal gameplay.
        if (x == 0 && y == 0 && width == sourceWidth && height == sourceHeight) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, minecraft.displayWidth, minecraft.displayHeight);
            GlStateManager.pushMatrix();
            try {
                renderAdapters(playback, sourceWidth, sourceHeight, partialTicks);
            } finally {
                GlStateManager.popMatrix();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            return;
        }

        double sourceAspect = minecraft.displayWidth / (double) Math.max(1, minecraft.displayHeight);
        double targetAspect = width / (double) height;
        int fittedWidth = width;
        int fittedHeight = height;
        if (targetAspect > sourceAspect) {
            fittedWidth = Math.max(1, (int) Math.round(height * sourceAspect));
        } else {
            fittedHeight = Math.max(1, (int) Math.round(width / sourceAspect));
        }

        int fittedX = x + (width - fittedWidth) / 2;
        int fittedY = y + (height - fittedHeight) / 2;
        float scaleX = fittedWidth / (float) sourceWidth;
        float scaleY = fittedHeight / (float) sourceHeight;

        int scaleFactor = sr.getScaleFactor();
        int scissorX = fittedX * scaleFactor;
        int scissorY = minecraft.displayHeight - (fittedY + fittedHeight) * scaleFactor;
        int scissorWidth = fittedWidth * scaleFactor;
        int scissorHeight = fittedHeight * scaleFactor;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, Math.max(1, scissorWidth), Math.max(1, scissorHeight));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(fittedX, fittedY, 0.0F);
            GlStateManager.scale(scaleX, scaleY, 1.0F);

            renderAdapters(playback, sourceWidth, sourceHeight, partialTicks);
        } finally {
            GlStateManager.popMatrix();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
    private void renderAdapters(ReplayPlayback playback, int sourceWidth, int sourceHeight, float partialTicks) {
        boolean debugOpen = minecraft.gameSettings.showDebugInfo;
        for (ReplayHudAdapter adapter : adapters) {
            if (!adapter.isActive()) continue;
            if (debugOpen && !adapter.isVisibleInDebug()) continue;
            try {
                adapter.render(playback, sourceWidth, sourceHeight, partialTicks);
            } catch (RuntimeException exception) {
                if (logger != null) {
                    logger.warn("Replay HUD adapter {} failed to render this frame.", adapter.getId(), exception);
                }
            }
        }
    }

}
