package com.konqasasas.localreplay.replay.hud.cyv;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Read-only, tolerant parser for CYV's stable key=value config file. */
final class CyvConfigSnapshot {

    private final Map<String, String> values;

    private CyvConfigSnapshot(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    static CyvConfigSnapshot load(Path file, Logger logger) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (file == null || !Files.isRegularFile(file)) {
            return new CyvConfigSnapshot(values);
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int equals = trimmed.indexOf('=');
                if (equals <= 0) continue;
                String key = trimmed.substring(0, equals).trim();
                String value = trimmed.substring(equals + 1).trim();
                if (!key.isEmpty()) values.put(key, value);
            }
        } catch (IOException exception) {
            if (logger != null) {
                logger.warn("Failed to read CYV config at {}. CYV replay HUD will use defaults.", file, exception);
            }
        }
        return new CyvConfigSnapshot(values);
    }

    boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    int getInt(String key, int fallback, int min, int max) {
        String value = values.get(key);
        if (value == null) return fallback;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    String getString(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    boolean isHudEnabled(String name, boolean fallback) {
        return getBoolean(name + "_enabled", fallback) && getBoolean(name + "_visible", true);
    }

    int getHudX(String name, int fallback) {
        return getInt(name + "_posx", fallback, -100000, 100000);
    }

    int getHudY(String name, int fallback) {
        return getInt(name + "_posy", fallback, -100000, 100000);
    }

    int getColor(String key, int fallback) {
        return parseColor(getString(key, ""), fallback);
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) return fallback;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return fallback;
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            try {
                long parsed = Long.parseLong(hex, 16);
                if (hex.length() == 6) return 0xFF000000 | (int) parsed;
                if (hex.length() == 8) return (int) parsed;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        if ("black".equals(value)) return 0xFF000000;
        if ("dark_blue".equals(value) || "darkblue".equals(value)) return 0xFF0000AA;
        if ("dark_green".equals(value) || "darkgreen".equals(value)) return 0xFF00AA00;
        if ("dark_aqua".equals(value) || "darkaqua".equals(value)) return 0xFF00AAAA;
        if ("dark_red".equals(value) || "darkred".equals(value)) return 0xFFAA0000;
        if ("dark_purple".equals(value) || "darkpurple".equals(value)) return 0xFFAA00AA;
        if ("gold".equals(value)) return 0xFFFFAA00;
        if ("gray".equals(value) || "grey".equals(value)) return 0xFFAAAAAA;
        if ("dark_gray".equals(value) || "dark_grey".equals(value) || "darkgray".equals(value)) return 0xFF555555;
        if ("blue".equals(value)) return 0xFF5555FF;
        if ("green".equals(value)) return 0xFF55FF55;
        if ("aqua".equals(value) || "cyan".equals(value)) return 0xFF55FFFF;
        if ("red".equals(value)) return 0xFFFF5555;
        if ("light_purple".equals(value) || "lightpurple".equals(value) || "pink".equals(value)) return 0xFFFF55FF;
        if ("yellow".equals(value)) return 0xFFFFFF55;
        if ("white".equals(value)) return 0xFFFFFFFF;
        return fallback;
    }
}
