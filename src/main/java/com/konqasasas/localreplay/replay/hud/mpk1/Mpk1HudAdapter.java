package com.konqasasas.localreplay.replay.hud.mpk1;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayKeyBindings;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import com.konqasasas.localreplay.replay.hud.ReplayHudAdapter;
import com.konqasasas.localreplay.replay.hud.ReplayHudState;
import com.konqasasas.localreplay.replay.hud.ReplayHudTimeline;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Read-only compatibility renderer for the original MPK Mod 1.x HUD.
 *
 * It intentionally has no binary dependency on MPK Mod. Layout/preferences
 * are snapshotted from config/mpkmod.cfg when the replay screen is opened and
 * all displayed historical values are derived from ReplayTick data.
 */
public final class Mpk1HudAdapter implements ReplayHudAdapter {

    private static final String MOD_ID = "mpkmod";

    private final Minecraft minecraft;
    private final ReplayHudTimeline timeline;
    private final ReplayKeyBindings recordedKeys;
    private final Path configPath;
    private final boolean active;
    private final Mpk1ConfigSnapshot config;
    private final DecimalFormat decimalFormat;
    private final int color1;
    private final int color2;

    public Mpk1HudAdapter(Minecraft minecraft, ReplayData data, ReplayHudTimeline timeline, Logger logger) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (data == null) throw new IllegalArgumentException("data cannot be null");
        if (timeline == null) throw new IllegalArgumentException("timeline cannot be null");

        this.minecraft = minecraft;
        this.timeline = timeline;
        this.recordedKeys = data.getMetadata().getRecordedKeyBindings();

        Path resolved = null;
        if (LocalReplayMod.getPaths() != null && LocalReplayMod.getPaths().getRootDirectory().getParent() != null) {
            resolved = LocalReplayMod.getPaths().getRootDirectory()
                    .getParent()
                    .resolve("config")
                    .resolve("mpkmod.cfg");
        }
        this.configPath = resolved;
        this.active = Loader.isModLoaded(MOD_ID) && configPath != null && Files.isRegularFile(configPath) && Files.isReadable(configPath);
        this.config = active ? Mpk1ConfigSnapshot.load(configPath, logger) : Mpk1ConfigSnapshot.empty();
        this.color1 = color(config.getColor1(), 0xFFFFAA00);
        this.color2 = color(config.getColor2(), 0xFFFFFFFF);
        this.decimalFormat = buildDecimalFormat(config.getPrecision(), config.isTrimZeros());

        if (logger != null && active) {
            logger.info("MPK Mod 1 replay HUD compatibility enabled (read-only config adapter). config={}", configPath);
        }
    }

    @Override
    public String getId() {
        return "MPK Mod 1";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void render(ReplayPlayback playback, int sourceWidth, int sourceHeight, float partialTicks) {
        if (!active || playback == null || !config.isShowLabels()) return;

        ReplayHudState state = timeline.getState(playback.getCurrentTickIndex());
        if (state == null) return;
        ReplayTick tick = state.getTick();

        // Current-session metrics such as FPS are intentionally unsupported in replay HUDs.
        drawLabel("labelcoordx", "X", format(tick.getX()));
        drawLabel("labelcoordy", "Y", format(tick.getY()));
        drawLabel("labelcoordz", "Z", format(tick.getZ()));
        drawLabel("labelfacing", "F", facingValue(tick.getYaw()));
        drawLabel("labelpitch", "Pitch", format(ReplayHudTimeline.wrapYaw(tick.getPitch())) + "°");

        drawLabel("labellastlandingx", "Last landing X", format(state.getLandingX()));
        drawLabel("labellastlandingy", "Last landing Y", format(state.getLandingY()));
        drawLabel("labellastlandingz", "Last landing Z", format(state.getLandingZ()));
        drawLabel("labellastturning", "Last turning", format(state.getLastTurning()));
        drawLabel("labellast45timing", "Last 45", format(state.getLast45()));
        drawLabel("labellasttiming", "Last Timing", state.getLastTiming());
        drawLabel("labellastinput", "Last Input", emptyTo(state.getLastInput(), "N/A"));

        int airtime = tick.isFlying() ? 0 : state.getAirtime();
        drawLabel("labelairtime", "Airtime", String.valueOf(airtime));

        drawLabel("labeljumpangle", "Jump Angle", format(ReplayHudTimeline.wrapYaw(state.getJumpYaw())) + "°");
        drawLabel("labeljumpcoordsx", "Jump X", format(state.getJumpX()));
        drawLabel("labeljumpcoordsy", "Jump Y", format(state.getJumpY()));
        drawLabel("labeljumpcoordsz", "Jump Z", format(state.getJumpZ()));

        drawLabel("labelhitangle", "Hit Angle", format(ReplayHudTimeline.wrapYaw(state.getHitYaw())) + "°");
        drawLabel("labelhitcoordsx", "Hit X", format(state.getHitX()));
        drawLabel("labelhitcoordsy", "Hit Y", format(state.getHitY()));
        drawLabel("labelhitcoordsz", "Hit Z", format(state.getHitZ()));

        drawSpeed(state);
        drawSpeedVector(state);
        drawMotion(tick);
        drawKeystrokes(tick);

        // Intentionally unsupported: PB/XPB/ZPB, landing-block offsets, total
        // offset, and millisecond timing. They depend on MPK runtime state that
        // Local Replay deliberately does not record or inspect.
    }

    private void drawSpeed(ReplayHudState state) {
        Mpk1ConfigSnapshot.LabelConfig label = visibleLabel("labelspeed");
        if (label == null) return;
        double multiplier = config.isSpeedBlocksPerSecond() ? 20.0D : 1.0D;
        String name = config.isSpeedBlocksPerSecond() ? "Speed (b/s)" : "Speed (b/t)";
        drawVectorLabel(label, name,
                state.getVelocityX() * multiplier,
                state.getVelocityY() * multiplier,
                state.getVelocityZ() * multiplier);
    }

    private void drawMotion(ReplayTick tick) {
        Mpk1ConfigSnapshot.LabelConfig label = visibleLabel("labelmotion");
        if (label == null) return;
        double multiplier = config.isSpeedBlocksPerSecond() ? 20.0D : 1.0D;
        String name = config.isSpeedBlocksPerSecond() ? "Motion (b/s)" : "Motion (b/t)";
        drawVectorLabel(label, name,
                tick.getMotionX() * multiplier,
                tick.getMotionY() * multiplier,
                tick.getMotionZ() * multiplier);
    }

    private void drawSpeedVector(ReplayHudState state) {
        Mpk1ConfigSnapshot.LabelConfig label = visibleLabel("labelspeedadvanced");
        if (label == null) return;

        double x = state.getVelocityX();
        double z = state.getVelocityZ();
        if (config.isSpeedBlocksPerSecond()) {
            x *= 20.0D;
            z *= 20.0D;
        }
        double magnitude = Math.sqrt(x * x + z * z);
        double angle = Math.toDegrees(Math.atan2(x, z));

        FontRenderer font = minecraft.fontRenderer;
        int xPos = label.getX(2);
        int yPos = label.getY(317);
        String prefix = "Speed Vector:";
        font.drawString(prefix, xPos, yPos, color1, true);
        int cursor = xPos + font.getStringWidth(prefix);
        cursor = drawSegment(format(magnitude), cursor, yPos, color2);
        cursor = drawSegment("/", cursor, yPos, color1);
        drawSegment(format(angle) + "°", cursor, yPos, color2);
    }

    private void drawVectorLabel(Mpk1ConfigSnapshot.LabelConfig label, String name, double x, double y, double z) {
        FontRenderer font = minecraft.fontRenderer;
        int xPos = label.getX(2);
        int yPos = label.getY(0);
        String prefix = name + ":";
        font.drawString(prefix, xPos, yPos, color1, true);
        int cursor = xPos + font.getStringWidth(prefix);
        cursor = drawSegment(format(x), cursor, yPos, color2);
        cursor = drawSegment("/", cursor, yPos, color1);
        cursor = drawSegment(format(y), cursor, yPos, color2);
        cursor = drawSegment("/", cursor, yPos, color1);
        drawSegment(format(z), cursor, yPos, color2);
    }

    private void drawLabel(String id, String name, String value) {
        Mpk1ConfigSnapshot.LabelConfig label = visibleLabel(id);
        if (label == null) return;
        FontRenderer font = minecraft.fontRenderer;
        int x = label.getX(2);
        int y = label.getY(0);
        String prefix = name + ":";
        font.drawString(prefix, x, y, color1, true);
        font.drawString(value == null ? "" : value, x + font.getStringWidth(prefix), y, color2, true);
    }

    private Mpk1ConfigSnapshot.LabelConfig visibleLabel(String id) {
        Mpk1ConfigSnapshot.LabelConfig label = config.getLabel(id);
        return label != null && label.isEnabledAndUsed() ? label : null;
    }

    private int drawSegment(String text, int x, int y, int color) {
        minecraft.fontRenderer.drawString(text, x, y, color, true);
        return x + minecraft.fontRenderer.getStringWidth(text);
    }

    private void drawKeystrokes(ReplayTick tick) {
        Mpk1ConfigSnapshot.LabelConfig master = visibleLabel("labelkeystrokes");
        if (master == null) return;
        int baseX = master.getX(0);
        int baseY = master.getY(0);

        drawKey("forward", baseX, baseY, recordedKeys.getForward(), tick.isForwardDown());
        drawKey("left", baseX, baseY, recordedKeys.getLeft(), tick.isLeftDown());
        drawKey("back", baseX, baseY, recordedKeys.getBack(), tick.isBackDown());
        drawKey("right", baseX, baseY, recordedKeys.getRight(), tick.isRightDown());
        drawKey("jump", baseX, baseY, recordedKeys.getJump(), tick.isJumpDown());
        drawKey("sprint", baseX, baseY, recordedKeys.getSprint(), tick.isSprintDown());
        drawKey("sneak", baseX, baseY, recordedKeys.getSneak(), tick.isSneakDown());
    }

    private void drawKey(String id, int baseX, int baseY, String rawText, boolean down) {
        Mpk1ConfigSnapshot.KeyConfig key = config.getKey(id);
        if (key == null || !key.isEnabledAndUsed()) return;

        int x = baseX + key.getX(0);
        int y = baseY + key.getY(0);
        int width = key.getSizeX(25);
        int height = key.getSizeY(25);
        if (width <= 0 || height <= 0) return;

        // Values match MPK Mod 1.3.0's fixed keystroke renderer.
        int background = down ? 0x5FFFFFFF : 0x2F1F1F1F;
        int textColor = down ? 0xFF000000 : 0xFFFFFFFF;
        Gui.drawRect(x, y, x + width, y + height, background);

        String text = compactKeyName(rawText);
        FontRenderer font = minecraft.fontRenderer;
        int textX = x + (width - font.getStringWidth(text)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawString(text, textX, textY, textColor);
    }

    private String facingValue(float yaw) {
        float wrapped = ReplayHudTimeline.wrapYaw(yaw);
        int index = (int) Math.floor(Math.abs(wrapped / 45.0F));
        String axis = (index == 0 || index == 3 || index == 4) ? "Z" : "X";
        return format(wrapped) + "°" + axis;
    }

    private String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        return decimalFormat.format(value);
    }

    private static DecimalFormat buildDecimalFormat(int precision, boolean trimZeros) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat format = new DecimalFormat("0", symbols);
        format.setGroupingUsed(false);
        format.setMaximumFractionDigits(precision);
        format.setMinimumFractionDigits(trimZeros ? 0 : precision);
        return format;
    }

    private static String compactKeyName(String value) {
        if (value == null) return "";
        String s = value.trim().toUpperCase(Locale.ROOT);
        if ("LCONTROL".equals(s)) return "LCTRL";
        if ("RCONTROL".equals(s)) return "RCTRL";
        return s;
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static int color(String name, int fallback) {
        if (name == null) return fallback;
        String value = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if ("black".equals(value)) return 0xFF000000;
        if ("dark_blue".equals(value)) return 0xFF0000AA;
        if ("dark_green".equals(value)) return 0xFF00AA00;
        if ("dark_aqua".equals(value)) return 0xFF00AAAA;
        if ("dark_red".equals(value)) return 0xFFAA0000;
        if ("dark_purple".equals(value)) return 0xFFAA00AA;
        if ("gold".equals(value)) return 0xFFFFAA00;
        if ("gray".equals(value) || "grey".equals(value)) return 0xFFAAAAAA;
        if ("dark_gray".equals(value) || "dark_grey".equals(value)) return 0xFF555555;
        if ("blue".equals(value)) return 0xFF5555FF;
        if ("green".equals(value)) return 0xFF55FF55;
        if ("aqua".equals(value)) return 0xFF55FFFF;
        if ("red".equals(value)) return 0xFFFF5555;
        if ("light_purple".equals(value)) return 0xFFFF55FF;
        if ("yellow".equals(value)) return 0xFFFFFF55;
        if ("white".equals(value)) return 0xFFFFFFFF;
        return fallback;
    }
}
