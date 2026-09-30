package com.konqasasas.localreplay.replay.data;

public final class ReplayMetadata {

    public static final int FORMAT_VERSION_V1 = 1;
    public static final int FORMAT_VERSION_V2 = 2;
    public static final int FORMAT_VERSION_CURRENT = FORMAT_VERSION_V2;

    private final int formatVersion;
    private final String minecraftVersion;
    private final String modVersion;
    private final long createdAtEpochMillis;
    private final int tickCount;
    private final ReplayKeyBindings recordedKeyBindings;

    public ReplayMetadata(
            int formatVersion,
            String minecraftVersion,
            String modVersion,
            long createdAtEpochMillis,
            int tickCount,
            ReplayKeyBindings recordedKeyBindings
    ) {
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("formatVersion must be positive");
        }
        if (createdAtEpochMillis < 0L) {
            throw new IllegalArgumentException("createdAtEpochMillis cannot be negative");
        }
        if (tickCount < 0) {
            throw new IllegalArgumentException("tickCount cannot be negative");
        }
        if (recordedKeyBindings == null) {
            throw new IllegalArgumentException("recordedKeyBindings cannot be null");
        }

        this.formatVersion = formatVersion;
        this.minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
        this.modVersion = requireText(modVersion, "modVersion");
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.tickCount = tickCount;
        this.recordedKeyBindings = recordedKeyBindings;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getModVersion() {
        return modVersion;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public int getTickCount() {
        return tickCount;
    }

    public ReplayKeyBindings getRecordedKeyBindings() {
        return recordedKeyBindings;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return trimmed;
    }
}
