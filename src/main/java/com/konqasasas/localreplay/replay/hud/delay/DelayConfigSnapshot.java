package com.konqasasas.localreplay.replay.hud.delay;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Read-only snapshot of Delay Mod 1.12.2's config/delaymod.cfg.
 *
 * Local Replay never writes this file. Missing/malformed values fall back to
 * the same defaults used by Delay Mod itself.
 */
public final class DelayConfigSnapshot {

    private static final int DEFAULT_HUD_X = 8;
    private static final int DEFAULT_HUD_Y = 8;
    private static final String DEFAULT_LABEL_TEXT = "Delay:";
    private static final String DEFAULT_VALUE_TEMPLATE = "{n}";
    private static final String DEFAULT_LABEL_COLOR = "gold";
    private static final String DEFAULT_VALUE_COLOR = "white";

    private final int hudX;
    private final int hudY;
    private final String labelText;
    private final String valueTemplate;
    private final int labelColor;
    private final int valueColor;

    private DelayConfigSnapshot(
            int hudX,
            int hudY,
            String labelText,
            String valueTemplate,
            int labelColor,
            int valueColor
    ) {
        this.hudX = hudX;
        this.hudY = hudY;
        this.labelText = labelText;
        this.valueTemplate = valueTemplate;
        this.labelColor = labelColor;
        this.valueColor = valueColor;
    }

    public static DelayConfigSnapshot load(Path configPath, Logger logger) {
        Properties properties = new Properties();
        if (configPath != null && Files.isRegularFile(configPath) && Files.isReadable(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            } catch (IOException exception) {
                if (logger != null) {
                    logger.warn("Could not read Delay Mod config for replay HUD compatibility: {}", configPath, exception);
                }
            }
        }

        int hudX = parseInt(properties.getProperty("hudX"), DEFAULT_HUD_X);
        int hudY = parseInt(properties.getProperty("hudY"), DEFAULT_HUD_Y);
        String labelText = properties.containsKey("labelText")
                ? properties.getProperty("labelText", DEFAULT_LABEL_TEXT)
                : DEFAULT_LABEL_TEXT;
        String valueTemplate = parseTemplate(properties.getProperty("valueTemplate"), DEFAULT_VALUE_TEMPLATE);
        int labelColor = colorFromName(properties.getProperty("labelColor"), DEFAULT_LABEL_COLOR);
        int valueColor = colorFromName(properties.getProperty("valueColor"), DEFAULT_VALUE_COLOR);

        return new DelayConfigSnapshot(hudX, hudY, labelText, valueTemplate, labelColor, valueColor);
    }

    public int getHudX() {
        return hudX;
    }

    public int getHudY() {
        return hudY;
    }

    public String getLabelText() {
        return labelText;
    }

    public String formatValue(int groundTicks) {
        return valueTemplate.replace("{n}", Integer.toString(groundTicks));
    }

    public int getLabelColor() {
        return labelColor;
    }

    public int getValueColor() {
        return valueColor;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String parseTemplate(String value, String fallback) {
        if (value == null || value.isEmpty() || !value.contains("{n}")) return fallback;
        return value;
    }

    private static int colorFromName(String name, String fallbackName) {
        String normalized = name == null ? fallbackName : name.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "black": return 0x000000;
            case "dark_blue": return 0x0000AA;
            case "dark_green": return 0x00AA00;
            case "dark_aqua": return 0x00AAAA;
            case "dark_red": return 0xAA0000;
            case "dark_purple": return 0xAA00AA;
            case "gold": return 0xFFAA00;
            case "gray": return 0xAAAAAA;
            case "dark_gray": return 0x555555;
            case "blue": return 0x5555FF;
            case "green": return 0x55FF55;
            case "aqua": return 0x55FFFF;
            case "red": return 0xFF5555;
            case "light_purple": return 0xFF55FF;
            case "yellow": return 0xFFFF55;
            case "white": return 0xFFFFFF;
            default:
                if (!fallbackName.equals(normalized)) {
                    return colorFromName(fallbackName, fallbackName);
                }
                return 0xFFFFFF;
        }
    }
}
