package com.konqasasas.localreplay.replay.data;

import java.util.Arrays;

public final class ReplayData {

    private final ReplayMetadata metadata;
    private final ReplayTick[] ticks;

    public ReplayData(ReplayMetadata metadata, ReplayTick[] ticks) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        if (ticks == null) {
            throw new IllegalArgumentException("ticks cannot be null");
        }
        if (metadata.getTickCount() != ticks.length) {
            throw new IllegalArgumentException("metadata tickCount does not match replay data");
        }

        this.ticks = Arrays.copyOf(ticks, ticks.length);
        for (int i = 0; i < this.ticks.length; i++) {
            if (this.ticks[i] == null) {
                throw new IllegalArgumentException("ticks[" + i + "] cannot be null");
            }
        }
        this.metadata = metadata;
    }

    public ReplayMetadata getMetadata() {
        return metadata;
    }

    public int size() {
        return ticks.length;
    }

    public boolean isEmpty() {
        return ticks.length == 0;
    }

    public ReplayTick getTick(int index) {
        if (index < 0 || index >= ticks.length) {
            throw new IndexOutOfBoundsException("Replay tick index out of range: " + index);
        }
        return ticks[index];
    }

    public ReplayTick[] copyTicks() {
        return Arrays.copyOf(ticks, ticks.length);
    }
}
