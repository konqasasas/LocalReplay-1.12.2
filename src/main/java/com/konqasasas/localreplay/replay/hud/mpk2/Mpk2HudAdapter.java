package com.konqasasas.localreplay.replay.hud.mpk2;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import com.konqasasas.localreplay.replay.hud.ReplayHudAdapter;
import com.konqasasas.localreplay.replay.hud.ReplayHudState;
import com.konqasasas.localreplay.replay.hud.ReplayHudTimeline;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only compatibility renderer for MPKMod2's configurable overlay.
 *
 * The adapter consumes only MPKMod2 JSON files plus generic ReplayTick state;
 * it never links against or reflects into MPKMod2 classes/runtime objects.
 */
public final class Mpk2HudAdapter implements ReplayHudAdapter {

    private static final String MOD_ID = "mpkmod";
    private static final double DEFAULT_FONT_SIZE = 9.0D;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final Map<String, String> COLOR_CODES = createColorCodes();

    private static final int INPUT_HISTORY_PREFERRED_WIDTH = 86;
    private static final int INPUT_HISTORY_MAX_MESSAGES = 30;
    private static final int INPUT_HISTORY_LINE_HEIGHT = 10;
    private static final int INPUT_HISTORY_FADE_TICKS = 20;

    private static final Mpk2ReplayTimeline.PosAndAngle ZERO_POS_AND_ANGLE =
            new Mpk2ReplayTimeline.PosAndAngle(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
    private static final Mpk2ReplayTimeline.Blip ZERO_BLIP =
            new Mpk2ReplayTimeline.Blip(0, 0, 0.0D, 0.0D, 0.0D);

    private final Minecraft minecraft;
    private final ReplayData data;
    private final ReplayHudTimeline timeline;
    private final Mpk2ReplayTimeline mpkTimeline;
    private final Path configPath;
    private final Path optionsPath;
    private final boolean active;
    private final Mpk2ConfigSnapshot config;

    public Mpk2HudAdapter(Minecraft minecraft, ReplayData data, ReplayHudTimeline timeline, Logger logger) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (data == null) throw new IllegalArgumentException("data cannot be null");
        if (timeline == null) throw new IllegalArgumentException("timeline cannot be null");

        this.minecraft = minecraft;
        this.data = data;
        this.timeline = timeline;
        this.mpkTimeline = new Mpk2ReplayTimeline(data);

        Path mcDir = null;
        if (LocalReplayMod.getPaths() != null) {
            mcDir = LocalReplayMod.getPaths().getRootDirectory().getParent();
        }
        Path folder = mcDir == null ? null : mcDir.resolve("config").resolve("mpk").resolve("config");
        this.configPath = folder == null ? null : folder.resolve("mpk-config.json");
        this.optionsPath = folder == null ? null : folder.resolve("mpk-options.json");
        this.active = Loader.isModLoaded(MOD_ID) && folder != null;
        this.config = active
                ? Mpk2ConfigSnapshot.load(configPath, optionsPath, logger)
                : Mpk2ConfigSnapshot.load(null, null, null);

        if (logger != null && active) {
            logger.info(
                    "MPKMod2 replay HUD compatibility enabled (read-only JSON adapter). config={} ({})",
                    configPath,
                    config.isCustomFileLoaded() ? "custom snapshot" : "default compatible layout"
            );
        }
    }

    @Override
    public String getId() {
        return "MPKMod2";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void render(ReplayPlayback playback, int sourceWidth, int sourceHeight, float partialTicks) {
        if (!active || playback == null || !config.isDisplayOverlay()) return;
        // MPKMod2's own overlay renderer returns immediately while F3 is open.
        // Mirror that behavior now that Local Replay can re-compose vanilla F3.
        if (minecraft.gameSettings.showDebugInfo) return;

        int index = playback.getCurrentTickIndex();
        ReplayHudState state = timeline.getState(index);
        Mpk2ReplayTimeline.State mpkState = mpkTimeline.getState(index);
        if (state == null || mpkState == null) return;

        for (Mpk2ConfigSnapshot.ComponentConfig component : config.getComponents()) {
            if (component.getKind() == Mpk2ConfigSnapshot.Kind.INFO_LABEL) {
                drawInfoLabel((Mpk2ConfigSnapshot.InfoLabelConfig) component, state, mpkState,
                        index, sourceWidth, sourceHeight);
            } else if (component.getKind() == Mpk2ConfigSnapshot.Kind.KEY_BINDING_LABEL) {
                drawKeyBinding((Mpk2ConfigSnapshot.KeyBindingConfig) component, state.getTick(), sourceWidth, sourceHeight);
            } else if (component.getKind() == Mpk2ConfigSnapshot.Kind.INPUT_HISTORY) {
                drawInputHistory((Mpk2ConfigSnapshot.InputHistoryConfig) component, index, sourceWidth, sourceHeight);
            }
        }
    }

    private void drawInfoLabel(
            Mpk2ConfigSnapshot.InfoLabelConfig label,
            ReplayHudState state,
            Mpk2ReplayTimeline.State mpkState,
            int index,
            int sourceWidth,
            int sourceHeight
    ) {
        String text = formatTemplate(label.getText(), state, mpkState, index);
        // A label containing an InfoVar Local Replay cannot truthfully recreate
        // is hidden as a whole instead of leaking a literal "{ip}"-style token.
        if (text == null) return;

        FontRenderer font = minecraft.fontRenderer;
        double fontSize = label.getFontSize();
        double scale = fontSize / DEFAULT_FONT_SIZE;
        if (!(scale > 0.0D) || Double.isInfinite(scale) || Double.isNaN(scale)) scale = 1.0D;

        double textWidth = font.getStringWidth("\u00A7r" + text) * scale;
        double textHeight = font.FONT_HEIGHT * scale;
        double[] pos = resolve(label.getPos(), textWidth, textHeight, sourceWidth, sourceHeight);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) pos[0], (float) pos[1], 0.0F);
            GlStateManager.scale((float) scale, (float) scale, 1.0F);
            font.drawString("\u00A7r" + text, 0.0F, 0.0F, label.getColor(), true);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawKeyBinding(
            Mpk2ConfigSnapshot.KeyBindingConfig key,
            ReplayTick tick,
            int sourceWidth,
            int sourceHeight
    ) {
        Boolean down = replayKeyState(key.getName(), tick);
        if (down == null) return;

        double width = resolveSize(key.getWidth(), sourceWidth,
                (key.getPos().getPercentFlag() & Mpk2ConfigSnapshot.PERCENT_SIZE_X) != 0);
        double height = resolveSize(key.getHeight(), sourceHeight,
                (key.getPos().getPercentFlag() & Mpk2ConfigSnapshot.PERCENT_SIZE_Y) != 0);
        if (width <= 0.0D || height <= 0.0D) return;

        double[] pos = resolve(key.getPos(), width, height, sourceWidth, sourceHeight);
        int left = (int) Math.round(pos[0]);
        int top = (int) Math.round(pos[1]);
        int right = (int) Math.round(pos[0] + width);
        int bottom = (int) Math.round(pos[1] + height);
        if (right <= left || bottom <= top) return;

        Gui.drawRect(left, top, right, bottom, down ? key.getKeyDownColor() : key.getKeyUpColor());

        String displayName = key.getDisplayName();
        FontRenderer font = minecraft.fontRenderer;
        int textWidth = font.getStringWidth(displayName);
        float centerX = (float) (pos[0] + width / 2.0D);
        float centerY = (float) (pos[1] + height / 2.0D + 1.0D);
        font.drawString(
                displayName,
                centerX - textWidth / 2.0F,
                centerY - font.FONT_HEIGHT / 2.0F,
                down ? 0xFF000000 : 0xFFFFFFFF,
                false
        );
    }

    private void drawInputHistory(
            Mpk2ConfigSnapshot.InputHistoryConfig history,
            int index,
            int sourceWidth,
            int sourceHeight
    ) {
        double width = resolveSize(history.getWidth(), sourceWidth,
                (history.getPos().getPercentFlag() & Mpk2ConfigSnapshot.PERCENT_SIZE_X) != 0);
        double height = resolveSize(history.getHeight(), sourceHeight,
                (history.getPos().getPercentFlag() & Mpk2ConfigSnapshot.PERCENT_SIZE_Y) != 0);
        if (width <= 0.0D || height <= 0.0D) return;

        double[] pos = resolve(history.getPos(), width, height, sourceWidth, sourceHeight);
        int left = (int) Math.round(pos[0]);
        int top = (int) Math.round(pos[1]);
        int right = (int) Math.round(pos[0] + width);
        int bottom = (int) Math.round(pos[1] + height);
        if (right <= left || bottom <= top) return;

        if (!history.isTransparentBackground()) {
            drawRectWithEdge(left, top, right, bottom, history.getBackgroundColor(), history.getEdgeColor());
        }

        List<HistoryMessage> messages = buildInputHistory(index);
        int maxDisplayed = (int) (((height - 2.0D) / INPUT_HISTORY_LINE_HEIGHT) - 0.5D);
        maxDisplayed = Math.max(0, maxDisplayed);
        int count = Math.min(messages.size(), maxDisplayed);

        for (int i = 0; i < count; i++) {
            HistoryMessage message = messages.get(i);
            double yOffset = history.isInverted()
                    ? height - (1 + i) * INPUT_HISTORY_LINE_HEIGHT
                    : (1 + i) * INPUT_HISTORY_LINE_HEIGHT;
            float centerY = (float) (pos[1] + yOffset);

            int fadeAlpha = inputHistoryFadeAlpha(message.tickAge, history.getMaxTickAge());
            int messageColor = withAlpha(history.getMessageColor(), fadeAlpha);

            drawMonospaceLeftCentered(message.keysPressed, (float) (pos[0] + 3.0D), centerY, messageColor);
            drawRightCentered(String.valueOf(message.ticksPressed),
                    (float) (pos[0] + INPUT_HISTORY_PREFERRED_WIDTH - 3.0D), centerY, messageColor);
        }
    }

    private String formatTemplate(
            String raw,
            ReplayHudState state,
            Mpk2ReplayTimeline.State mpkState,
            int index
    ) {
        String text = raw == null ? "" : raw;
        for (Map.Entry<String, String> entry : COLOR_CODES.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            Token token = Token.parse(matcher.group(1));
            Resolution resolution = resolveInfoVar(token.name, state, mpkState, index);
            if (!resolution.supported) return null;

            // Supported replay-derived InfoVars always render. Event-derived
            // values such as lastLanding/lastHit/lastJump/lastBlip/last45 use
            // zero defaults until the first real event occurs. Unsupported
            // current-session values are still rejected above.
            if (resolution.value == null) {
                resolution = Resolution.of(0.0D);
            }

            String replacement = formatValue(resolution.value, token.decimals, token.keepZeros);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Resolution resolveInfoVar(
            String name,
            ReplayHudState commonState,
            Mpk2ReplayTimeline.State mpkState,
            int index
    ) {
        ReplayTick tick = mpkState.getTick();
        ReplayTick previous = data.getTick(Math.max(0, index - 1));

        VectorValue pos = new VectorValue(tick.getX(), tick.getY(), tick.getZ());
        VectorValue lastPos = new VectorValue(previous.getX(), previous.getY(), previous.getZ());
        VectorValue motion = new VectorValue(tick.getMotionX(), tick.getMotionY(), tick.getMotionZ());
        VectorValue speed = new VectorValue(
                index <= 0 ? 0.0D : tick.getX() - previous.getX(),
                index <= 0 ? 0.0D : tick.getY() - previous.getY(),
                index <= 0 ? 0.0D : tick.getZ() - previous.getZ()
        );

        Resolution vector = resolveVector(name, "pos", pos);
        if (vector != null) return vector;
        vector = resolveVector(name, "lastPos", lastPos);
        if (vector != null) return vector;
        vector = resolveVector(name, "motion", motion);
        if (vector != null) return vector;
        vector = resolveVector(name, "speed", speed);
        if (vector != null) return vector;

        if ("yaw".equals(name)) return Resolution.of(ReplayHudTimeline.wrapYaw(tick.getYaw()));
        if ("trueYaw".equals(name)) return Resolution.of(tick.getYaw());
        if ("pitch".equals(name)) return Resolution.of(ReplayHudTimeline.wrapYaw(tick.getPitch()));
        if ("truePitch".equals(name)) return Resolution.of(tick.getPitch());
        if ("facing".equals(name)) return Resolution.of(facing(tick.getYaw()));

        if ("airtime".equals(name)) return Resolution.of(mpkState.getAirtime());
        if ("tier".equals(name)) return Resolution.of(-(mpkState.getAirtime() - 12));
        if ("last45".equals(name)) return Resolution.of(mpkState.getLast45() == null ? 0.0F : mpkState.getLast45());
        if ("lastTiming".equals(name)) return Resolution.of(emptyTo(commonState.getLastTiming(), "None"));
        if ("lastInputs".equals(name)) return Resolution.of(mpk2InputString(tick));
        if ("preturn".equals(name)) return Resolution.of(mpkState.getPreturn());
        if ("deltaYaw".equals(name)) return Resolution.of(mpkState.getDeltaYaw());
        if ("deltaPitch".equals(name)) return Resolution.of(mpkState.getDeltaPitch());
        if ("onGround".equals(name)) return Resolution.of(tick.isOnGround());
        if ("sprinting".equals(name)) return Resolution.of(tick.isSprinting());
        if ("flying".equals(name)) return Resolution.of(tick.isFlying());
        if ("grinds".equals(name)) return Resolution.of(mpkState.getGrinds());
        if ("lastSidestep".equals(name)) return Resolution.of(mpkState.getLastSidestep());
        if ("compressedInputHistory".equals(name)) return Resolution.of(compressedInputHistory(index));

        Resolution posAngle = resolvePosAndAngle(name, "lastLanding", mpkState.getLastLanding());
        if (posAngle != null) return posAngle;
        posAngle = resolvePosAndAngle(name, "lastHit", mpkState.getLastHit());
        if (posAngle != null) return posAngle;
        posAngle = resolvePosAndAngle(name, "lastJump", mpkState.getLastJump());
        if (posAngle != null) return posAngle;

        Resolution blip = resolveBlip(name, mpkState.getLastBlip());
        if (blip != null) return blip;

        if ("mcVersion".equals(name)) return Resolution.of(data.getMetadata().getMinecraftVersion());

        // Deliberately unsupported: current-session/environment values such as
        // fps, time, date, ip, ping, username and sprintToggled,
        // latestLB/PB/offset state, bounding boxes, raw mouse history and any
        // third-party/custom module InfoVars.
        return Resolution.unsupported();
    }

    private static Resolution resolveVector(String name, String prefix, VectorValue value) {
        if (prefix.equals(name)) return Resolution.of(value);
        if ((prefix + ".x").equals(name)) return Resolution.of(value.x);
        if ((prefix + ".y").equals(name)) return Resolution.of(value.y);
        if ((prefix + ".z").equals(name)) return Resolution.of(value.z);
        if ((prefix + ".lengthXZ").equals(name)) return Resolution.of(value.lengthXZ());
        if ((prefix + ".angleXZ").equals(name)) return Resolution.of(value.angleXZ());
        return null;
    }

    private static Resolution resolvePosAndAngle(
            String name,
            String prefix,
            Mpk2ReplayTimeline.PosAndAngle value
    ) {
        if (!name.equals(prefix) && !name.startsWith(prefix + ".")) return null;
        if (value == null) value = ZERO_POS_AND_ANGLE;

        if (prefix.equals(name)) return Resolution.of(value);
        if ((prefix + ".trueYaw").equals(name)) return Resolution.of(value.trueYaw);
        if ((prefix + ".truePitch").equals(name)) return Resolution.of(value.truePitch);
        if ((prefix + ".yaw").equals(name)) return Resolution.of(value.yaw);
        if ((prefix + ".pitch").equals(name)) return Resolution.of(value.pitch);

        VectorValue pos = new VectorValue(value.x, value.y, value.z);
        Resolution nested = resolveVector(name, prefix + ".pos", pos);
        return nested == null ? Resolution.unsupported() : nested;
    }

    private static Resolution resolveBlip(String name, Mpk2ReplayTimeline.Blip value) {
        String prefix = "lastBlip";
        if (!name.equals(prefix) && !name.startsWith(prefix + ".")) return null;
        if (value == null) value = ZERO_BLIP;

        if (prefix.equals(name)) return Resolution.of(value);
        if ((prefix + ".chainedBlips").equals(name)) return Resolution.of(value.chainedBlips);
        if ((prefix + ".lastChainedBlips").equals(name)) return Resolution.of(value.lastChainedBlips);

        VectorValue pos = new VectorValue(value.x, value.y, value.z);
        Resolution nested = resolveVector(name, prefix + ".pos", pos);
        return nested == null ? Resolution.unsupported() : nested;
    }

    private String compressedInputHistory(int index) {
        int start = Math.max(0, index - 99);
        List<TimingGroup> groups = new ArrayList<TimingGroup>();
        for (int i = start; i <= index; i++) {
            ReplayTick tick = data.getTick(i);
            Mpk2ReplayTimeline.State state = mpkTimeline.getState(i);
            String timing = timingInputString(tick, state != null && state.isJumpTick());
            if (!groups.isEmpty() && groups.get(groups.size() - 1).timing.equals(timing)) {
                groups.get(groups.size() - 1).count++;
            } else {
                groups.add(new TimingGroup(timing));
            }
        }

        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) out.append(", ");
            TimingGroup group = groups.get(i);
            out.append("Tuple{").append(group.count).append(", ").append(group.timing).append('}');
        }
        return out.append(']').toString();
    }

    private static String timingInputString(ReplayTick tick, boolean jumpTick) {
        StringBuilder s = new StringBuilder(10);

        int forwardBack = (tick.isForwardDown() ? 1 : 0) - (tick.isBackDown() ? 1 : 0);
        int leftRight = (tick.isLeftDown() ? 1 : 0) - (tick.isRightDown() ? 1 : 0);
        if (forwardBack > 0) s.append('W');
        else if (forwardBack < 0) s.append('S');
        if (leftRight > 0) s.append('A');
        else if (leftRight < 0) s.append('D');

        if (tick.isSprinting()) s.append('P');
        if (tick.isSneakDown()) s.append('N');
        if (jumpTick) s.append('J');
        if (tick.isOnGround()) s.append('G');
        else s.append("!G");
        return s.toString();
    }

    private List<HistoryMessage> buildInputHistory(int index) {
        List<HistoryMessage> messages = new ArrayList<HistoryMessage>();
        if (index < 0 || data.isEmpty()) return messages;

        String currentKeys = null;
        int currentCount = 0;
        int groupEnd = index;

        for (int i = index; i >= 0; i--) {
            String keys = inputHistoryKeys(data.getTick(i));
            if (currentKeys == null) {
                currentKeys = keys;
                currentCount = 1;
                groupEnd = i;
                continue;
            }

            if (currentKeys.equals(keys)) {
                currentCount++;
                continue;
            }

            messages.add(new HistoryMessage(
                    currentKeys,
                    currentCount,
                    index - groupEnd + 1
            ));
            if (messages.size() >= INPUT_HISTORY_MAX_MESSAGES) return messages;

            currentKeys = keys;
            currentCount = 1;
            groupEnd = i;
        }

        if (currentKeys != null && messages.size() < INPUT_HISTORY_MAX_MESSAGES) {
            messages.add(new HistoryMessage(currentKeys, currentCount, index - groupEnd + 1));
        }
        return messages;
    }

    private static String inputHistoryKeys(ReplayTick tick) {
        int forwardBack = (tick.isForwardDown() ? 1 : 0) - (tick.isBackDown() ? 1 : 0);
        int leftRight = (tick.isRightDown() ? 1 : 0) - (tick.isLeftDown() ? 1 : 0);

        String movement;
        if (forwardBack == 0 && leftRight == 0) movement = " ";
        else if (forwardBack < 0 && leftRight == 0) movement = "⬇";
        else if (forwardBack > 0 && leftRight == 0) movement = "⬆";
        else if (forwardBack == 0 && leftRight < 0) movement = "⬅";
        else if (forwardBack == 0 && leftRight > 0) movement = "➡";
        else if (forwardBack > 0 && leftRight > 0) movement = "⬈";
        else if (forwardBack > 0) movement = "⬉";
        else if (leftRight > 0) movement = "⬊";
        else movement = "⬋";

        StringBuilder out = new StringBuilder(10);
        out.append(movement).append(' ');
        out.append(tick.isJumpDown() ? 'J' : ' ').append(' ');
        out.append(tick.isSneakDown() ? "Sn" : "  ").append(' ');
        out.append(tick.isSprintDown() ? "Sp" : "  ");
        return out.toString();
    }

    private static int inputHistoryFadeAlpha(int tickAge, int maxTickAge) {
        if (tickAge < maxTickAge - INPUT_HISTORY_FADE_TICKS) return 255;
        int remaining = maxTickAge - Math.min(tickAge, maxTickAge);
        int alpha = (int) Math.round((remaining / (double) INPUT_HISTORY_FADE_TICKS) * 255.0D);
        return Math.max(0, Math.min(255, alpha));
    }

    private void drawMonospaceLeftCentered(String text, float x, float centerY, int color) {
        FontRenderer font = minecraft.fontRenderer;
        float y = centerY - font.FONT_HEIGHT / 2.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') continue;
            font.drawString(String.valueOf(c), x + i * 6.0F, y, color, true);
        }
    }

    private void drawRightCentered(String text, float rightX, float centerY, int color) {
        FontRenderer font = minecraft.fontRenderer;
        float x = rightX - font.getStringWidth(text);
        float y = centerY - font.FONT_HEIGHT / 2.0F;
        font.drawString(text, x, y, color, true);
    }

    private static void drawRectWithEdge(int left, int top, int right, int bottom, int background, int edge) {
        Gui.drawRect(left, top, right, bottom, background);
        Gui.drawRect(left, top, right, top + 1, edge);
        Gui.drawRect(left, bottom - 1, right, bottom, edge);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, edge);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, edge);
    }

    private static int withAlpha(int argb, int alpha) {
        int clamped = Math.max(4, Math.min(255, alpha));
        return (argb & 0x00FFFFFF) | (clamped << 24);
    }

    private static String formatValue(Object value, int decimals, boolean keepZeros) {
        if (value instanceof Double || value instanceof Float) {
            return formatNumber(((Number) value).doubleValue(), decimals, keepZeros);
        }
        if (value instanceof VectorValue) {
            VectorValue v = (VectorValue) value;
            return "[" + formatNumber(v.x, decimals, keepZeros) + ","
                    + formatNumber(v.y, decimals, keepZeros) + ","
                    + formatNumber(v.z, decimals, keepZeros) + "]";
        }
        if (value instanceof Mpk2ReplayTimeline.PosAndAngle) {
            Mpk2ReplayTimeline.PosAndAngle v = (Mpk2ReplayTimeline.PosAndAngle) value;
            return "[" + formatNumber(v.yaw, decimals, keepZeros) + ", "
                    + formatNumber(v.pitch, decimals, keepZeros) + ", ["
                    + formatNumber(v.x, decimals, keepZeros) + ","
                    + formatNumber(v.y, decimals, keepZeros) + ","
                    + formatNumber(v.z, decimals, keepZeros) + "]]";
        }
        if (value instanceof Mpk2ReplayTimeline.Blip) {
            Mpk2ReplayTimeline.Blip v = (Mpk2ReplayTimeline.Blip) value;
            return "[" + v.chainedBlips + ", " + v.lastChainedBlips + ", ["
                    + formatNumber(v.x, decimals, keepZeros) + ","
                    + formatNumber(v.y, decimals, keepZeros) + ","
                    + formatNumber(v.z, decimals, keepZeros) + "]]";
        }
        return String.valueOf(value);
    }

    private static String formatNumber(double number, int decimals, boolean keepZeros) {
        DecimalFormat format = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(false);
        int clamped = Math.max(0, Math.min(16, decimals));
        format.setMaximumFractionDigits(clamped);
        format.setMinimumFractionDigits(keepZeros ? clamped : 0);
        return format.format(number);
    }

    private static String facing(float trueYaw) {
        double yaw = ReplayHudTimeline.wrapYaw(trueYaw);
        int xz = (int) Math.floor(Math.abs(yaw / 45.0D));
        return xz == 0 || xz == 3 || xz == 4 ? "Z" : "X";
    }

    private static String mpk2InputString(ReplayTick tick) {
        StringBuilder s = new StringBuilder(7);
        if (tick.isForwardDown()) s.append('W');
        if (tick.isLeftDown()) s.append('A');
        if (tick.isBackDown()) s.append('S');
        if (tick.isRightDown()) s.append('D');
        if (tick.isSprintDown()) s.append('P');
        if (tick.isSneakDown()) s.append('N');
        if (tick.isJumpDown()) s.append('J');
        return s.toString();
    }

    private static Boolean replayKeyState(String name, ReplayTick tick) {
        if (name == null) return null;
        if ("key.forward".equals(name)) return tick.isForwardDown();
        if ("key.left".equals(name)) return tick.isLeftDown();
        if ("key.back".equals(name)) return tick.isBackDown();
        if ("key.right".equals(name)) return tick.isRightDown();
        if ("key.jump".equals(name)) return tick.isJumpDown();
        if ("key.sprint".equals(name)) return tick.isSprintDown();
        if ("key.sneak".equals(name)) return tick.isSneakDown();
        return null;
    }

    private static double[] resolve(
            Mpk2ConfigSnapshot.PosSpec pos,
            double rawWidth,
            double rawHeight,
            double parentWidth,
            double parentHeight
    ) {
        int flags = pos.getPercentFlag();
        double width = resolveSize(rawWidth, parentWidth, (flags & Mpk2ConfigSnapshot.PERCENT_SIZE_X) != 0);
        double height = resolveSize(rawHeight, parentHeight, (flags & Mpk2ConfigSnapshot.PERCENT_SIZE_Y) != 0);

        Mpk2ConfigSnapshot.AnchorValue anchor = pos.getAnchor();
        Mpk2ConfigSnapshot.AnchorValue parentAnchor = pos.getParentAnchor();
        double xOffset = pos.getX()
                * ((flags & Mpk2ConfigSnapshot.PERCENT_POS_X) != 0 ? parentWidth : 1.0D)
                * parentAnchor.getMultiplierX();
        double yOffset = pos.getY()
                * ((flags & Mpk2ConfigSnapshot.PERCENT_POS_Y) != 0 ? parentHeight : 1.0D)
                * parentAnchor.getMultiplierY();

        double x = parentAnchor.getOriginX() * parentWidth + xOffset - anchor.getOriginX() * width;
        double y = parentAnchor.getOriginY() * parentHeight + yOffset - anchor.getOriginY() * height;
        return new double[] {x, y};
    }

    private static double resolveSize(double raw, double parent, boolean percent) {
        if (percent) return parent * Math.max(0.0D, Math.min(1.0D, raw));
        return raw >= 0.0D ? raw : parent + raw;
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static Map<String, String> createColorCodes() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("black", "\u00A70");
        map.put("dblue", "\u00A71");
        map.put("dgreen", "\u00A72");
        map.put("daqua", "\u00A73");
        map.put("dred", "\u00A74");
        map.put("dpurple", "\u00A75");
        map.put("gold", "\u00A76");
        map.put("gray", "\u00A77");
        map.put("dgray", "\u00A78");
        map.put("blue", "\u00A79");
        map.put("green", "\u00A7a");
        map.put("aqua", "\u00A7b");
        map.put("red", "\u00A7c");
        map.put("lpurple", "\u00A7d");
        map.put("yellow", "\u00A7e");
        map.put("white", "\u00A7f");
        map.put("reset", "\u00A7r");
        map.put("obfuscated", "\u00A7k");
        map.put("bold", "\u00A7l");
        map.put("strikethrough", "\u00A7m");
        map.put("underline", "\u00A7n");
        map.put("italic", "\u00A7o");
        return map;
    }

    private static final class Resolution {
        final boolean supported;
        final Object value;

        private Resolution(boolean supported, Object value) {
            this.supported = supported;
            this.value = value;
        }

        static Resolution of(Object value) { return new Resolution(true, value); }
        static Resolution unsupported() { return new Resolution(false, null); }
    }

    private static final class VectorValue {
        final double x;
        final double y;
        final double z;

        VectorValue(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double lengthXZ() {
            return Math.sqrt(x * x + z * z);
        }

        double angleXZ() {
            return Math.toDegrees(Math.atan2(x, z));
        }
    }

    private static final class HistoryMessage {
        final String keysPressed;
        final int ticksPressed;
        final int tickAge;

        HistoryMessage(String keysPressed, int ticksPressed, int tickAge) {
            this.keysPressed = keysPressed;
            this.ticksPressed = ticksPressed;
            this.tickAge = tickAge;
        }
    }

    private static final class TimingGroup {
        final String timing;
        int count = 1;

        TimingGroup(String timing) {
            this.timing = timing;
        }
    }

    private static final class Token {
        final String name;
        final int decimals;
        final boolean keepZeros;

        Token(String name, int decimals, boolean keepZeros) {
            this.name = name;
            this.decimals = decimals;
            this.keepZeros = keepZeros;
        }

        static Token parse(String raw) {
            if (raw == null) return new Token("", 3, false);
            String value = raw.trim();
            int comma = value.lastIndexOf(',');
            if (comma <= 0 || comma >= value.length() - 1) {
                return new Token(value, 3, false);
            }

            String suffix = value.substring(comma + 1).trim();
            boolean keep = suffix.endsWith("!");
            if (keep) suffix = suffix.substring(0, suffix.length() - 1);
            try {
                int decimals = Integer.parseInt(suffix);
                if (decimals < 0 || decimals > 16) return new Token(value, 3, false);
                return new Token(value.substring(0, comma).trim(), decimals, keep);
            } catch (NumberFormatException ignored) {
                return new Token(value, 3, false);
            }
        }
    }
}
