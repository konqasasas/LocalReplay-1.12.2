package com.konqasasas.localreplay.replay.hud.cyv;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import com.konqasasas.localreplay.replay.hud.ReplayHudAdapter;
import com.konqasasas.localreplay.replay.hud.ReplayHudState;
import com.konqasasas.localreplay.replay.hud.ReplayHudTimeline;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Local Replay's read-only CYV compatibility renderer.
 *
 * No CYV Java classes or runtime fields are accessed. Layout/style preferences
 * come from config/cyvforge/config.txt; values come exclusively from replay
 * data, so seek/playback never mutates or depends on the real player.
 */
public final class CyvHudAdapter implements ReplayHudAdapter {

    private static final String MOD_ID = "cyvforge";

    private final Minecraft minecraft;
    private final ReplayHudTimeline timeline;
    private final Path configPath;
    private final Logger logger;
    private final boolean active;

    private CyvConfigSnapshot config;
    private int color1;
    private int color2;
    private DecimalFormat decimalFormat;

    public CyvHudAdapter(Minecraft minecraft, ReplayHudTimeline timeline, Logger logger) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (timeline == null) throw new IllegalArgumentException("timeline cannot be null");
        this.minecraft = minecraft;
        this.timeline = timeline;

        Path resolvedConfigPath = null;
        if (LocalReplayMod.getPaths() != null) {
            resolvedConfigPath = LocalReplayMod.getPaths().getRootDirectory()
                    .getParent()
                    .resolve("config")
                    .resolve("cyvforge")
                    .resolve("config.txt");
        }
        this.configPath = resolvedConfigPath;
        this.logger = logger;
        this.active = Loader.isModLoaded(MOD_ID) && configPath != null && Files.isRegularFile(configPath) && Files.isReadable(configPath);
        reloadConfig();

        if (logger != null && active) {
            logger.info("CYV replay HUD compatibility enabled (read-only config adapter). config={}", configPath);
        }
    }

    private void reloadConfig() {
        this.config = CyvConfigSnapshot.load(configPath, logger);
        this.color1 = config.getColor("color1", 0xFF55FFFF);
        this.color2 = config.getColor("color2", 0xFFFFFFFF);

        int decimals = config.getInt("df", 5, 0, 12);
        boolean trimZeroes = config.getBoolean("trimZeroes", true);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        DecimalFormat format = new DecimalFormat("0", symbols);
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(decimals);
        format.setMinimumFractionDigits(trimZeroes ? 0 : decimals);
        this.decimalFormat = format;
    }

    @Override
    public String getId() {
        return "CYV";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void render(ReplayPlayback playback, int sourceWidth, int sourceHeight, float partialTicks) {
        if (!active || playback == null || sourceWidth <= 0 || sourceHeight <= 0) return;
        ReplayHudState state = timeline.getState(playback.getCurrentTickIndex());
        if (state == null) return;
        renderHud(state, sourceWidth, sourceHeight);
    }

    private void renderHud(ReplayHudState state, int sourceWidth, int sourceHeight) {
        ReplayTick tick = state.getTick();

        // Current-session metrics such as FPS are intentionally unsupported in replay HUDs.
        drawLabel("labelX", 0, 10, true, "X: ", format(tick.getX()));
        drawLabel("labelY", 0, 19, true, "Y: ", format(tick.getY()));
        drawLabel("labelZ", 0, 28, true, "Z: ", format(tick.getZ()));
        drawLabel("labelYaw", 0, 37, true, "F: ", format(ReplayHudTimeline.wrapYaw(tick.getYaw())) + "°");
        drawLabel("labelPitch", 0, 46, true, "Pitch: ", format(tick.getPitch()) + "°");

        drawLabel("labelLastTiming", 0, 56, true, "Last Timing: ", state.getLastTiming());
        drawLabel("labelAirtime", 0, 65, true, "Airtime: ", String.valueOf(state.getLastAirtime()));

        drawLabel("labelLandingX", 0, 75, true, "Landing X: ", format(state.getLandingX()));
        drawLabel("labelLandingY", 0, 84, true, "Landing Y: ", format(state.getLandingY()));
        drawLabel("labelLandingZ", 0, 93, true, "Landing Z: ", format(state.getLandingZ()));

        drawLabel("labelJumpAngle", 0, 103, true, "Jump Angle: ", format(ReplayHudTimeline.wrapYaw(state.getJumpYaw())) + "°");
        drawLabel("labelSecondTurn", 0, 112, true, "Second Turn: ", format(ReplayHudTimeline.wrapYaw(state.getSecondTurn())));
        drawLabel("labelPreturn", 0, 121, true, "Preturn: ", format(ReplayHudTimeline.wrapYaw(state.getPreturn())) + "°");

        drawLabel("labelHitX", 0, 131, true, "Hit X: ", format(state.getHitX()));
        drawLabel("labelHitY", 0, 140, true, "Hit Y: ", format(state.getHitY()));
        drawLabel("labelHitZ", 0, 149, true, "Hit Z: ", format(state.getHitZ()));

        drawLabel("labelJumpX", 0, 159, false, "Jump X: ", format(state.getJumpX()));
        drawLabel("labelJumpY", 0, 168, false, "Jump Y: ", format(state.getJumpY()));
        drawLabel("labelJumpZ", 0, 177, false, "Jump Z: ", format(state.getJumpZ()));

        drawLabel("labelXSpeed", 0, 187, false, "X Speed: ", format(state.getVelocityX()));
        drawLabel("labelYSpeed", 0, 196, false, "Y Speed: ", format(state.getVelocityY()));
        drawLabel("labelZSpeed", 0, 205, false, "Z Speed: ", format(state.getVelocityZ()));

        drawLabel("labelIndividualSpeeds", 0, 214, false, "Speeds: ",
                format(state.getVelocityX()) + "/" + format(state.getVelocityY()) + "/" + format(state.getVelocityZ()));
        double speed = Math.hypot(state.getVelocityX(), state.getVelocityZ());
        double speedAngle = Math.toDegrees(Math.atan2(
                state.getVelocityX() == 0.0D ? 0.0D : -state.getVelocityX(),
                state.getVelocityZ()));
        drawLabel("labelSpeedVector", 0, 223, false, "Speed Vector: ",
                format(speed) + "/" + format(speedAngle) + "°");

        drawLabel("labelLast45", 177, 74, false, "Last 45: ", format(state.getLast45()));
        drawLabel("labelLastTurningYaw", 177, 83, false, "Last Turning: ", format(state.getLastTurning()));
        drawLabel("labelLastInput", 177, 92, false, "Last Input: ", state.getLastInput());
        drawLabel("labelLastSidestep", 177, 101, false, "Last Sidestep: ", sidestepText(state));
        drawLabel("labelTier", 177, 137, false, "Tier: ", String.valueOf(12 - state.getLastAirtime()));
        drawLabel("labelHitAngle", 177, 146, true, "Hit Angle: ",
                format(ReplayHudTimeline.wrapYaw(state.getHitYaw())) + "°");
        double hitSpeed = Math.hypot(state.getHitVelocityX(), state.getHitVelocityZ());
        double hitAngle = Math.toDegrees(Math.atan2(
                state.getHitVelocityX() == 0.0D ? 0.0D : -state.getHitVelocityX(),
                state.getHitVelocityZ()));
        drawLabel("labelHitVector", 177, 155, false, "Hit Vector: ",
                format(hitSpeed) + "/" + format(hitAngle) + "°");
        drawLabel("labelBlips", 177, 164, false, "Blips: ",
                state.getBlips() + " chained / Y: " + format(state.getLastBlipHeight()));
        drawLabel("labelGrinds", 177, 173, false, "Grinds: ", String.valueOf(state.getGrinds()));

        drawDirectionHud(state, sourceWidth);
        drawKeystrokes(state);
        drawTurningHud(state);
    }

    private void drawLabel(String name, int defaultX, int defaultY, boolean defaultEnabled, String prefix, String value) {
        if (!config.isHudEnabled(name, defaultEnabled)) return;
        int x = config.getHudX(name, defaultX) + 1;
        int y = config.getHudY(name, defaultY) + 1;
        FontRenderer font = minecraft.fontRenderer;
        font.drawStringWithShadow(prefix, x, y, color1);
        font.drawStringWithShadow(value == null ? "" : value, x + font.getStringWidth(prefix), y, color2);
    }

    private void drawDirectionHud(ReplayHudState state, int sourceWidth) {
        if (!config.isHudEnabled("directionHUD", true)) return;
        FontRenderer font = minecraft.fontRenderer;
        int width = 120;
        int x = sourceWidth / 2 - width / 2;
        int y = 0;
        float facing = state.getTick().getYaw() % 360.0F;
        if (facing < 0.0F) facing += 360.0F;

        for (float i = 0.0F; i < 360.0F; i += 22.5F) {
            float distance = Math.abs(facing - i) <= 180.0F
                    ? i - facing
                    : facing > 180.0F ? i - (facing - 360.0F) : (i - 360.0F) - facing;
            if (Math.abs(distance) > 95.0F) continue;
            int offset = (int) (distance * 0.5F * width / 100.0F);
            int lineHeight = i % 90.0F == 0.0F
                    ? font.FONT_HEIGHT * 2 / 3
                    : i % 45.0F == 0.0F ? font.FONT_HEIGHT / 2 : font.FONT_HEIGHT / 3;
            drawVerticalLine(x + width / 2 + offset, y + 1, y + 1 + lineHeight, 3.0F, 0xFFFFFFFF);
            String cardinal = null;
            if (i == 0.0F) cardinal = "S";
            else if (i == 90.0F) cardinal = "W";
            else if (i == 180.0F) cardinal = "N";
            else if (i == 270.0F) cardinal = "E";
            if (cardinal != null) {
                font.drawStringWithShadow(cardinal,
                        x + width / 2 + offset - font.getStringWidth(cardinal) / 2,
                        y + 2 + lineHeight,
                        0xFFFFFFFF);
            }
        }
        drawVerticalLine(x + width / 2, y + 1, y + font.FONT_HEIGHT * 3 / 2, 3.0F, 0xFFFF0000);
    }

    private void drawKeystrokes(ReplayHudState state) {
        if (!config.isHudEnabled("keystrokes", true)) return;
        int currentSize = config.getInt("keystrokesSize", 66, 40, 200);
        int baseX = config.getHudX("keystrokes", 250);
        int baseY = config.getHudY("keystrokes", 0);
        int sq = currentSize / 3;
        int split = currentSize / 2;
        int shortHeight = currentSize / 5;
        int spaceHeight = currentSize / 5;

        drawKey(baseX + sq + 1, baseY + 1, sq - 2, sq - 2, "W", state.getTick().isForwardDown(), currentSize);
        drawKey(baseX + 1, baseY + sq + 1, sq - 2, sq - 2, "A", state.getTick().isLeftDown(), currentSize);
        drawKey(baseX + sq + 1, baseY + sq + 1, sq - 2, sq - 2, "S", state.getTick().isBackDown(), currentSize);
        drawKey(baseX + 2 * sq + 1, baseY + sq + 1, sq - 2, sq - 2, "D", state.getTick().isRightDown(), currentSize);
        drawKey(baseX + 1, baseY + 2 * sq + 1, split - 2, shortHeight - 2, "Spr", state.getTick().isSprintDown(), currentSize);
        drawKey(baseX + split + 1, baseY + 2 * sq + 1, split - 2, shortHeight - 2, "Snk", state.getTick().isSneakDown(), currentSize);
        drawKey(baseX + 1, baseY + 2 * sq + shortHeight + 1, currentSize - 2, spaceHeight - 2,
                "-----", state.getTick().isJumpDown(), currentSize);
    }

    private void drawKey(int x, int y, int width, int height, String name, boolean down, int currentSize) {
        if (width <= 0 || height <= 0) return;
        int background = down ? 0x66FFFFFF : 0x66141414;
        drawRoundedRect(x, y, x + width, y + height, 2.0F, background);
        int textColor = down ? color1 : 0xFFFFFFFF;
        float fontScale = currentSize / 66.0F;
        FontRenderer font = minecraft.fontRenderer;
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(fontScale, fontScale, 1.0F);
        font.drawStringWithShadow(name,
                -font.getStringWidth(name) / 2.0F,
                -font.FONT_HEIGHT / 2.0F + 1.0F,
                textColor);
        GlStateManager.popMatrix();
    }

    private void drawTurningHud(ReplayHudState state) {
        // Newer CYV configs expose these fields; old 1.12 configs simply omit
        // them, so the adapter remains inert and backwards compatible.
        if (!config.isHudEnabled("turnHUDMaster", false)) return;
        int min = config.getInt("turnHUDAngleMin", 1, 1, 12);
        int max = config.getInt("turnHUDAngleMax", 12, 1, 12);
        boolean split = config.getBoolean("splitTurningHUD", false);
        FontRenderer font = minecraft.fontRenderer;

        if (!split && config.isHudEnabled("turnHUDSingle", true)) {
            int x = config.getHudX("turnHUDSingle", 250);
            int y = config.getHudY("turnHUDSingle", 100);
            int row = 0;
            for (int tick = Math.min(min, max); tick <= Math.max(min, max); tick++) {
                String prefix = tick + ": ";
                String value = format(ReplayHudTimeline.wrapYaw(state.getTurningAngle(tick - 1))) + "°";
                font.drawStringWithShadow(prefix, x + 1, y + 1 + row * 9, color1);
                font.drawStringWithShadow(value, x + 1 + font.getStringWidth(prefix), y + 1 + row * 9, color2);
                row++;
            }
            return;
        }

        if (split) {
            for (int tick = Math.min(min, max); tick <= Math.max(min, max); tick++) {
                String name = "labelTurnAngle" + tick;
                if (!config.isHudEnabled(name, true)) continue;
                int x = config.getHudX(name, 250);
                int y = config.getHudY(name, 100 + (tick - 1) * 9);
                String prefix = tick + ": ";
                String value = format(ReplayHudTimeline.wrapYaw(state.getTurningAngle(tick - 1))) + "°";
                font.drawStringWithShadow(prefix, x + 1, y + 1, color1);
                font.drawStringWithShadow(value, x + 1 + font.getStringWidth(prefix), y + 1, color2);
            }
        }
    }

    private String sidestepText(ReplayHudState state) {
        if (state.getSidestep() == 0) return "WAD " + state.getSidestepTime() + " ticks";
        if (state.getSidestep() == 1) return "WDWA";
        return "None";
    }

    private String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return decimalFormat.format(value);
    }

    private static void drawVerticalLine(float x, float y1, float y2, float thickness, int color) {
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(thickness);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x, y1);
        GL11.glVertex2f(x, y2);
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void drawRoundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        float maxRadius = Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F);
        radius = Math.max(0.0F, Math.min(radius, maxRadius));

        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        float red = (color >>> 16 & 0xFF) / 255.0F;
        float green = (color >>> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GL11.glPushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.enableColorMaterial();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(red, green, blue, alpha);

        if (radius <= 0.0F) {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x2, y1);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x1, y2);
            GL11.glEnd();
        } else {
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f((x1 + x2) * 0.5F, (y1 + y2) * 0.5F);
            addRoundedCornerVertices(x1 + radius, y1 + radius, radius, 180, 270);
            addRoundedCornerVertices(x2 - radius, y1 + radius, radius, 270, 360);
            addRoundedCornerVertices(x2 - radius, y2 - radius, radius, 0, 90);
            addRoundedCornerVertices(x1 + radius, y2 - radius, radius, 90, 180);
            GL11.glVertex2f(x1, y1 + radius);
            GL11.glEnd();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GL11.glPopMatrix();
    }

    private static void addRoundedCornerVertices(float centerX, float centerY, float radius, int startDegrees, int endDegrees) {
        final int segments = 9;
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * (i / (double) segments));
            GL11.glVertex2f(
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius);
        }
    }
}
