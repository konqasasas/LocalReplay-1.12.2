package com.konqasasas.localreplay.replay.hud.mpk1;

import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Read-only parser for the Forge Configuration text format used by MPK Mod 1.x. */
final class Mpk1ConfigSnapshot {

    static final class LabelConfig {
        private final Map<String, String> values;

        LabelConfig(Map<String, String> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
        }

        boolean isEnabledAndUsed() {
            return getBoolean(values, "enabled", true) && getBoolean(values, "used", false);
        }

        int getX(int fallback) {
            return getInt(values, "x", fallback, -100000, 100000);
        }

        int getY(int fallback) {
            return getInt(values, "y", fallback, -100000, 100000);
        }
    }

    static final class KeyConfig {
        private final Map<String, String> values;

        KeyConfig(Map<String, String> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
        }

        boolean isEnabledAndUsed() {
            return getBoolean(values, "enabled", true) && getBoolean(values, "used", false);
        }

        int getX(int fallback) {
            return getInt(values, "x", fallback, -100000, 100000);
        }

        int getY(int fallback) {
            return getInt(values, "y", fallback, -100000, 100000);
        }

        int getSizeX(int fallback) {
            return getInt(values, "sizex", fallback, 1, 10000);
        }

        int getSizeY(int fallback) {
            return getInt(values, "sizey", fallback, 1, 10000);
        }
    }

    private final Map<String, String> gui;
    private final Map<String, LabelConfig> labels;
    private final Map<String, KeyConfig> keys;

    private Mpk1ConfigSnapshot(
            Map<String, String> gui,
            Map<String, LabelConfig> labels,
            Map<String, KeyConfig> keys
    ) {
        this.gui = Collections.unmodifiableMap(new LinkedHashMap<String, String>(gui));
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<String, LabelConfig>(labels));
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<String, KeyConfig>(keys));
    }

    static Mpk1ConfigSnapshot load(Path file, Logger logger) {
        Map<String, String> gui = new LinkedHashMap<String, String>();
        Map<String, Map<String, String>> labelValues = new LinkedHashMap<String, Map<String, String>>();
        Map<String, Map<String, String>> keyValues = new LinkedHashMap<String, Map<String, String>>();

        if (file == null || !Files.isRegularFile(file)) {
            return empty();
        }

        Deque<String> stack = new ArrayDeque<String>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                if ("}".equals(trimmed)) {
                    if (!stack.isEmpty()) stack.removeLast();
                    continue;
                }

                if (trimmed.endsWith("{")) {
                    String name = normalizeSectionName(trimmed.substring(0, trimmed.length() - 1).trim());
                    if (!name.isEmpty()) stack.addLast(name);
                    continue;
                }

                int colon = trimmed.indexOf(':');
                int equals = trimmed.indexOf('=', colon + 1);
                if (colon <= 0 || equals <= colon + 1 || stack.isEmpty()) continue;

                String key = trimmed.substring(colon + 1, equals).trim().toLowerCase(Locale.ROOT);
                String value = trimmed.substring(equals + 1).trim();
                if (key.isEmpty()) continue;

                String first = stack.peekFirst();
                if ("gui".equals(first)) {
                    gui.put(key, value);
                    continue;
                }

                if ("labels".equals(first) && stack.size() >= 2) {
                    String label = nth(stack, 1);
                    putNested(labelValues, label, key, value);
                    continue;
                }

                if ("keystrokes".equals(first)) {
                    int keyRoot = indexOf(stack, "keystroke_key");
                    if (keyRoot >= 0 && stack.size() > keyRoot + 1) {
                        // The Forge config may introduce another category level
                        // (for example Local Replay's own keybindings). The leaf
                        // category is still the individual KeyBinding name.
                        String keyName = nth(stack, stack.size() - 1);
                        putNested(keyValues, keyName, key, value);
                    }
                }
            }
        } catch (IOException exception) {
            if (logger != null) {
                logger.warn("Failed to read MPK Mod config at {}. MPK replay HUD will stay disabled/defaulted.", file, exception);
            }
        }

        Map<String, LabelConfig> labels = new LinkedHashMap<String, LabelConfig>();
        for (Map.Entry<String, Map<String, String>> entry : labelValues.entrySet()) {
            labels.put(entry.getKey(), new LabelConfig(entry.getValue()));
        }
        Map<String, KeyConfig> keys = new LinkedHashMap<String, KeyConfig>();
        for (Map.Entry<String, Map<String, String>> entry : keyValues.entrySet()) {
            keys.put(entry.getKey(), new KeyConfig(entry.getValue()));
        }
        return new Mpk1ConfigSnapshot(gui, labels, keys);
    }

    static Mpk1ConfigSnapshot empty() {
        return new Mpk1ConfigSnapshot(
                Collections.<String, String>emptyMap(),
                Collections.<String, LabelConfig>emptyMap(),
                Collections.<String, KeyConfig>emptyMap()
        );
    }

    boolean isShowLabels() {
        return getBoolean(gui, "showlabels", true);
    }

    boolean isSpeedBlocksPerSecond() {
        return getBoolean(gui, "speedlabelblockspersecond", false);
    }

    boolean isTrimZeros() {
        return getBoolean(gui, "trimzeros", false);
    }

    int getPrecision() {
        return getInt(gui, "coordsprecision", 5, 0, 16);
    }

    String getColor1() {
        return getString(gui, "color1", "gold");
    }

    String getColor2() {
        return getString(gui, "color2", "white");
    }

    LabelConfig getLabel(String id) {
        if (id == null) return null;
        return labels.get(id.toLowerCase(Locale.ROOT));
    }

    KeyConfig getKey(String name) {
        if (name == null) return null;
        return keys.get(name.toLowerCase(Locale.ROOT));
    }

    private static void putNested(
            Map<String, Map<String, String>> parent,
            String section,
            String key,
            String value
    ) {
        String normalized = section.toLowerCase(Locale.ROOT);
        Map<String, String> values = parent.get(normalized);
        if (values == null) {
            values = new LinkedHashMap<String, String>();
            parent.put(normalized, values);
        }
        values.put(key.toLowerCase(Locale.ROOT), value);
    }

    private static String normalizeSectionName(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String nth(Deque<String> stack, int index) {
        int i = 0;
        for (String value : stack) {
            if (i++ == index) return value;
        }
        return "";
    }

    private static int indexOf(Deque<String> stack, String target) {
        int i = 0;
        for (String value : stack) {
            if (target.equals(value)) return i;
            i++;
        }
        return -1;
    }

    private static boolean getBoolean(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int getInt(Map<String, String> values, String key, int fallback, int min, int max) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String getString(Map<String, String> values, String key, String fallback) {
        String value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }
}
