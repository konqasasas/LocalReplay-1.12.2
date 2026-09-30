package com.konqasasas.localreplay.replay.io;

import java.nio.file.Path;

/** Lightweight header information used by the Replay Library list. */
public final class ReplayFileSummary {

    private final Path path;
    private final int formatVersion;
    private final String minecraftVersion;
    private final String modVersion;
    private final long createdAtEpochMillis;
    private final int tickCount;

    public ReplayFileSummary(
            Path path,
            int formatVersion,
            String minecraftVersion,
            String modVersion,
            long createdAtEpochMillis,
            int tickCount
    ) {
        if (path == null) throw new IllegalArgumentException("path cannot be null");
        if (formatVersion <= 0) throw new IllegalArgumentException("formatVersion must be positive");
        if (minecraftVersion == null || minecraftVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("minecraftVersion cannot be blank");
        }
        if (modVersion == null || modVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("modVersion cannot be blank");
        }
        if (createdAtEpochMillis < 0L) throw new IllegalArgumentException("createdAtEpochMillis cannot be negative");
        if (tickCount <= 0) throw new IllegalArgumentException("tickCount must be positive");

        this.path = path.toAbsolutePath().normalize();
        this.formatVersion = formatVersion;
        this.minecraftVersion = minecraftVersion.trim();
        this.modVersion = modVersion.trim();
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.tickCount = tickCount;
    }

    public Path getPath() {
        return path;
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

    public double getDurationSeconds() {
        return tickCount / 20.0D;
    }
}
