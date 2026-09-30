package com.konqasasas.localreplay.replay.hud.delay;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.hud.ReplayHudAdapter;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Read-only compatibility renderer for Delay Mod 1.12.2.
 *
 * No Delay Mod classes/runtime state are accessed. Position/text/colors are
 * snapshotted from config/delaymod.cfg and the displayed counter is rebuilt
 * solely from ReplayTick.onGround history, so seeking is deterministic.
 */
public final class DelayHudAdapter implements ReplayHudAdapter {

    private static final String MOD_ID = "delaymod";

    private final Minecraft minecraft;
    private final DelayReplayTimeline timeline;
    private final DelayConfigSnapshot config;
    private final boolean active;

    public DelayHudAdapter(Minecraft minecraft, ReplayData data, Logger logger) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (data == null) throw new IllegalArgumentException("data cannot be null");

        this.minecraft = minecraft;
        this.timeline = new DelayReplayTimeline(data);
        this.active = Loader.isModLoaded(MOD_ID);

        Path configPath = minecraft.mcDataDir == null
                ? null
                : minecraft.mcDataDir.toPath().resolve("config").resolve("delaymod.cfg");
        this.config = DelayConfigSnapshot.load(configPath, logger);

        if (logger != null && active) {
            logger.info("Delay Mod replay HUD compatibility enabled (read-only config adapter). config={}", configPath);
        }
    }

    @Override
    public String getId() {
        return "Delay Mod";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /** Delay Mod renders from RenderGameOverlayEvent.Text even while F3 is open. */
    @Override
    public boolean isVisibleInDebug() {
        return true;
    }

    @Override
    public void render(ReplayPlayback playback, int sourceWidth, int sourceHeight, float partialTicks) {
        if (!active || playback == null || sourceWidth <= 0 || sourceHeight <= 0) return;

        FontRenderer font = minecraft.fontRenderer;
        if (font == null) return;

        int groundTicks = timeline.getGroundTicks(playback.getCurrentTickIndex());
        String label = config.getLabelText();
        String value = config.formatValue(groundTicks);

        int labelWidth = font.getStringWidth(label);
        int hudWidth = labelWidth + font.getStringWidth(value) + (labelWidth > 0 ? 4 : 0);
        int hudHeight = font.FONT_HEIGHT;
        int x = clamp(config.getHudX(), 0, Math.max(0, sourceWidth - hudWidth));
        int y = clamp(config.getHudY(), 0, Math.max(0, sourceHeight - hudHeight));

        font.drawStringWithShadow(label, x, y, config.getLabelColor());
        font.drawStringWithShadow(value, x + labelWidth + (labelWidth > 0 ? 4 : 0), y, config.getValueColor());
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        return Math.min(value, max);
    }
}
