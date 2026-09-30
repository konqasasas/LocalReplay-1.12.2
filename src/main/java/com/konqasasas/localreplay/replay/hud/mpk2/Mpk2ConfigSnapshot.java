package com.konqasasas.localreplay.replay.hud.mpk2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only snapshot of the stable, public MPKMod2 HUD JSON format.
 *
 * Only component types Local Replay can reproduce from generic replay data are
 * retained. Unknown/new component types and fields are deliberately ignored so
 * future MPKMod2 additions do not break replay rendering.
 */
final class Mpk2ConfigSnapshot {

    enum Kind {
        INFO_LABEL,
        KEY_BINDING_LABEL,
        INPUT_HISTORY
    }

    enum AnchorValue {
        CENTER(0.5D, 0.5D, 1.0D, 1.0D),
        CENTER_LEFT(0.0D, 0.5D, 1.0D, 1.0D),
        CENTER_RIGHT(1.0D, 0.5D, -1.0D, 1.0D),
        TOP_LEFT(0.0D, 0.0D, 1.0D, 1.0D),
        TOP_RIGHT(1.0D, 0.0D, -1.0D, 1.0D),
        TOP_CENTER(0.5D, 0.0D, 1.0D, 1.0D),
        BOTTOM_LEFT(0.0D, 1.0D, 1.0D, -1.0D),
        BOTTOM_RIGHT(1.0D, 1.0D, -1.0D, -1.0D),
        BOTTOM_CENTER(0.5D, 1.0D, 1.0D, -1.0D);

        private final double originX;
        private final double originY;
        private final double multiplierX;
        private final double multiplierY;

        AnchorValue(double originX, double originY, double multiplierX, double multiplierY) {
            this.originX = originX;
            this.originY = originY;
            this.multiplierX = multiplierX;
            this.multiplierY = multiplierY;
        }

        double getOriginX() { return originX; }
        double getOriginY() { return originY; }
        double getMultiplierX() { return multiplierX; }
        double getMultiplierY() { return multiplierY; }

        static AnchorValue parse(String value, AnchorValue fallback) {
            if (value == null) return fallback;
            try {
                return AnchorValue.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }

        static AnchorValue legacyFromSigned(double x, double y) {
            if (x < 0.0D) {
                return y < 0.0D ? BOTTOM_RIGHT : TOP_RIGHT;
            }
            return y < 0.0D ? BOTTOM_LEFT : TOP_LEFT;
        }
    }

    static final class PosSpec {
        private final double x;
        private final double y;
        private final AnchorValue anchor;
        private final AnchorValue parentAnchor;
        private final int percentFlag;

        PosSpec(double x, double y, AnchorValue anchor, AnchorValue parentAnchor, int percentFlag) {
            this.x = x;
            this.y = y;
            this.anchor = anchor;
            this.parentAnchor = parentAnchor;
            this.percentFlag = percentFlag & 0x0F;
        }

        double getX() { return x; }
        double getY() { return y; }
        AnchorValue getAnchor() { return anchor; }
        AnchorValue getParentAnchor() { return parentAnchor; }
        int getPercentFlag() { return percentFlag; }
    }

    static abstract class ComponentConfig {
        private final Kind kind;
        private final PosSpec pos;

        ComponentConfig(Kind kind, PosSpec pos) {
            this.kind = kind;
            this.pos = pos;
        }

        Kind getKind() { return kind; }
        PosSpec getPos() { return pos; }
    }

    static final class InfoLabelConfig extends ComponentConfig {
        private final String text;
        private final int color;
        private final double fontSize;

        InfoLabelConfig(PosSpec pos, String text, int color, double fontSize) {
            super(Kind.INFO_LABEL, pos);
            this.text = text == null ? "" : text;
            this.color = color;
            this.fontSize = fontSize;
        }

        String getText() { return text; }
        int getColor() { return color; }
        double getFontSize() { return fontSize; }
    }

    static final class KeyBindingConfig extends ComponentConfig {
        private final double width;
        private final double height;
        private final String name;
        private final String displayName;
        private final int keyDownColor;
        private final int keyUpColor;

        KeyBindingConfig(
                PosSpec pos,
                double width,
                double height,
                String name,
                String displayName,
                int keyDownColor,
                int keyUpColor
        ) {
            super(Kind.KEY_BINDING_LABEL, pos);
            this.width = width;
            this.height = height;
            this.name = name == null ? "" : name;
            this.displayName = displayName == null ? this.name : displayName;
            this.keyDownColor = keyDownColor;
            this.keyUpColor = keyUpColor;
        }

        double getWidth() { return width; }
        double getHeight() { return height; }
        String getName() { return name; }
        String getDisplayName() { return displayName; }
        int getKeyDownColor() { return keyDownColor; }
        int getKeyUpColor() { return keyUpColor; }
    }

    static final class InputHistoryConfig extends ComponentConfig {
        private final double width;
        private final double height;
        private final int backgroundColor;
        private final int messageColor;
        private final int edgeColor;
        private final boolean inverted;
        private final int maxTickAge;
        private final boolean transparentBackground;

        InputHistoryConfig(
                PosSpec pos,
                double width,
                double height,
                int backgroundColor,
                int messageColor,
                int edgeColor,
                boolean inverted,
                int maxTickAge,
                boolean transparentBackground
        ) {
            super(Kind.INPUT_HISTORY, pos);
            this.width = width;
            this.height = height;
            this.backgroundColor = backgroundColor;
            this.messageColor = messageColor;
            this.edgeColor = edgeColor;
            this.inverted = inverted;
            this.maxTickAge = maxTickAge;
            this.transparentBackground = transparentBackground;
        }

        double getWidth() { return width; }
        double getHeight() { return height; }
        int getBackgroundColor() { return backgroundColor; }
        int getMessageColor() { return messageColor; }
        int getEdgeColor() { return edgeColor; }
        boolean isInverted() { return inverted; }
        int getMaxTickAge() { return maxTickAge; }
        boolean isTransparentBackground() { return transparentBackground; }
    }

    // Mirrors ComponentHolder.PERCENT without linking to MPKMod2 classes.
    static final int PERCENT_POS_X = 1;
    static final int PERCENT_POS_Y = 1 << 1;
    static final int PERCENT_SIZE_X = 1 << 2;
    static final int PERCENT_SIZE_Y = 1 << 3;

    private final List<ComponentConfig> components;
    private final boolean displayOverlay;
    private final boolean customFileLoaded;

    private Mpk2ConfigSnapshot(List<ComponentConfig> components, boolean displayOverlay, boolean customFileLoaded) {
        this.components = Collections.unmodifiableList(new ArrayList<ComponentConfig>(components));
        this.displayOverlay = displayOverlay;
        this.customFileLoaded = customFileLoaded;
    }

    static Mpk2ConfigSnapshot load(Path customConfigFile, Path optionsFile, Logger logger) {
        boolean displayOverlay = readDisplayOverlay(optionsFile, logger);
        List<ComponentConfig> components = null;
        boolean loaded = false;

        if (customConfigFile != null && Files.isRegularFile(customConfigFile) && Files.isReadable(customConfigFile)) {
            try (BufferedReader reader = Files.newBufferedReader(customConfigFile, StandardCharsets.UTF_8)) {
                JsonElement root = new JsonParser().parse(reader);
                if (root != null && root.isJsonArray()) {
                    components = parseComponents(root.getAsJsonArray());
                    loaded = true;
                }
            } catch (Exception exception) {
                if (logger != null) {
                    logger.warn("Failed to read MPKMod2 HUD config at {}. Falling back to its standard replay-compatible layout.", customConfigFile, exception);
                }
            }
        }

        if (components == null) {
            components = defaultReplayCompatibleComponents();
        }
        return new Mpk2ConfigSnapshot(components, displayOverlay, loaded);
    }

    List<ComponentConfig> getComponents() {
        return components;
    }

    boolean isDisplayOverlay() {
        return displayOverlay;
    }

    boolean isCustomFileLoaded() {
        return customFileLoaded;
    }

    private static List<ComponentConfig> parseComponents(JsonArray array) {
        List<ComponentConfig> result = new ArrayList<ComponentConfig>();
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String className = string(object, "@class", "");
            PosSpec pos = parsePos(object.get("pos"));

            if (className.endsWith(".InfoLabel")) {
                String text = string(object, "text", "");
                int color = parseColor(string(object, "color", null), 0xFFFFFFFF);
                double fontSize = finiteDouble(object, "fontSize", 9.0D, 1.0D, 100.0D);
                result.add(new InfoLabelConfig(pos, text, color, fontSize));
                continue;
            }

            if (className.endsWith(".KeyBindingLabel")) {
                JsonObject size = object.has("size") && object.get("size").isJsonObject()
                        ? object.getAsJsonObject("size")
                        : null;
                double width = finiteDouble(size, "x", 21.0D, -100000.0D, 100000.0D);
                double height = finiteDouble(size, "y", 21.0D, -100000.0D, 100000.0D);
                String name = string(object, "name", "");
                String displayName = string(object, "displayName", name);
                int down = parseColor(string(object, "keyDownColor", null), 0x5FFFFFFF);
                int up = parseColor(string(object, "keyUpColor", null), 0x2F1F1F1F);
                result.add(new KeyBindingConfig(pos, width, height, name, displayName, down, up));
                continue;
            }

            if (className.endsWith(".InputHistory")) {
                JsonObject size = object.has("size") && object.get("size").isJsonObject()
                        ? object.getAsJsonObject("size")
                        : null;
                double width = finiteDouble(size, "x", 86.0D, -100000.0D, 100000.0D);
                double height = finiteDouble(size, "y", 120.0D, -100000.0D, 100000.0D);
                int background = parseColor(string(object, "backgroundColor", null), 0x28646464);
                int message = parseColor(string(object, "messageColor", null), 0xFFFFFFFF);
                int edge = parseColor(string(object, "edgeColor", null), 0x32646464);
                boolean inverted = boolValue(object, "inverted", false);
                int maxTickAge = intValue(object, "maxTickAge", 80, 20, 300);
                boolean transparentBackground = boolValue(object, "transparentBackground", false);
                result.add(new InputHistoryConfig(
                        pos,
                        width,
                        height,
                        background,
                        message,
                        edge,
                        inverted,
                        maxTickAge,
                        transparentBackground
                ));
            }
            // MessageQueue (offset runtime), BarrierDisplay, plots/angle paths
            // (raw mouse movement), and custom module components are ignored.
        }
        return result;
    }

    private static PosSpec parsePos(JsonElement element) {
        JsonObject pos = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        double rawX = finiteDouble(pos, "x", 0.0D, -1000000.0D, 1000000.0D);
        double rawY = finiteDouble(pos, "y", 0.0D, -1000000.0D, 1000000.0D);

        String anchorName = string(pos, "anchor", null);
        String parentAnchorName = string(pos, "parentAnchor", null);
        if (anchorName == null && parentAnchorName == null) {
            AnchorValue legacy = AnchorValue.legacyFromSigned(rawX, rawY);
            return new PosSpec(Math.abs(rawX), Math.abs(rawY), legacy, legacy, 0);
        }

        AnchorValue anchor = AnchorValue.parse(anchorName, AnchorValue.TOP_LEFT);
        AnchorValue parentAnchor = AnchorValue.parse(parentAnchorName, anchor);
        int percent = intValue(pos, "percentFlag", 0, 0, 15);
        return new PosSpec(rawX, rawY, anchor, parentAnchor, percent);
    }

    private static boolean readDisplayOverlay(Path optionsFile, Logger logger) {
        if (optionsFile == null || !Files.isRegularFile(optionsFile) || !Files.isReadable(optionsFile)) return true;
        try (BufferedReader reader = Files.newBufferedReader(optionsFile, StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonArray()) return true;
            for (JsonElement element : root.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!"displayOverlay".equals(string(object, "name", ""))) continue;
                JsonElement value = object.get("value");
                return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
                        ? true
                        : value.getAsBoolean();
            }
        } catch (Exception exception) {
            if (logger != null) {
                logger.warn("Failed to read MPKMod2 options at {}. Assuming Display Overlay is enabled.", optionsFile, exception);
            }
        }
        return true;
    }

    private static List<ComponentConfig> defaultReplayCompatibleComponents() {
        List<ComponentConfig> result = new ArrayList<ComponentConfig>();
        // Reimplementation of the supported subset of MPKMod2's public default
        // preset. Runtime-only IP/offset components are deliberately omitted.
        result.add(info(5, 49, "{gold}X: {white}{pos.x,5}"));
        result.add(info(5, 58, "{gold}Y: {white}{pos.y,5}"));
        result.add(info(5, 67, "{gold}Z: {white}{pos.z,5}"));
        result.add(info(5, 80, "{gold}Yaw: {white}{yaw,5!} {gold}{facing}"));
        result.add(info(5, 90, "{gold}Pitch: {white}{pitch,5}"));
        result.add(info(5, 25, "{gold}Time: {white}{time}"));
        result.add(info(5, 35, "{gold}Date: {white}{date}"));
        result.add(info(5, 103, "{gold}Tier: {white}{tier}"));
        result.add(info(5, 113, "{gold}Last 45: {white}{last45,5}"));
        result.add(info(5, 5, "{gold}FPS: {white}{fps}"));
        result.add(info(5, 123, "{gold}Last Timing:{reset} {lastTiming}"));
        result.add(info(5, 138, "{gold}Last Land X: {reset}{lastLanding.pos.x,5}"));
        result.add(info(5, 148, "{gold}Last Land Y: {reset}{lastLanding.pos.y,5}"));
        result.add(info(5, 158, "{gold}Last Land Z: {reset}{lastLanding.pos.z,5}"));

        result.add(key(-41, 78, 21, 21, "key.forward", "W"));
        result.add(key(-64, 101, 21, 21, "key.left", "A"));
        result.add(key(-41, 101, 21, 21, "key.back", "S"));
        result.add(key(-18, 101, 21, 21, "key.right", "D"));
        result.add(key(-18, 125, 31, 21, "key.sneak", "Shift"));
        result.add(key(-53, 125, 32, 21, "key.sprint", "Ctrl"));
        result.add(key(-18, 149, 67, 21, "key.jump", "Space"));
        return result;
    }

    private static InfoLabelConfig info(double x, double y, String text) {
        return new InfoLabelConfig(parseLegacyPos(x, y), text, 0xFFFFFFFF, 9.0D);
    }

    private static KeyBindingConfig key(double x, double y, double w, double h, String name, String displayName) {
        return new KeyBindingConfig(parseLegacyPos(x, y), w, h, name, displayName, 0x5FFFFFFF, 0x2F1F1F1F);
    }

    private static PosSpec parseLegacyPos(double x, double y) {
        AnchorValue anchor = AnchorValue.legacyFromSigned(x, y);
        return new PosSpec(Math.abs(x), Math.abs(y), anchor, anchor, 0);
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || key == null || !object.has(key)) return fallback;
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean boolValue(JsonObject object, String key, boolean fallback) {
        if (object == null || key == null || !object.has(key)) return fallback;
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback, int min, int max) {
        if (object == null || key == null || !object.has(key)) return fallback;
        try {
            int value = object.get(key).getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double finiteDouble(JsonObject object, String key, double fallback, double min, double max) {
        if (object == null || key == null || !object.has(key)) return fallback;
        try {
            double value = object.get(key).getAsDouble();
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) return fallback;
        String value = raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            long parsed = Long.parseLong(value, 16);
            if (value.length() == 6) parsed |= 0xFF000000L;
            if (value.length() != 6 && value.length() != 8) return fallback;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
