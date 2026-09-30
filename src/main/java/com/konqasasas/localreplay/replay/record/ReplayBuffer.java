package com.konqasasas.localreplay.replay.record;

import com.konqasasas.localreplay.replay.data.ReplayTick;

/**
 * Fixed-capacity ring buffer used for Local Replay's rolling recording.
 * The buffer owns no Minecraft state and can be tested independently.
 */
public final class ReplayBuffer {

    private final ReplayTick[] entries;
    private int writeIndex;
    private int size;

    public ReplayBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.entries = new ReplayTick[capacity];
    }

    public int capacity() {
        return entries.length;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == entries.length;
    }

    public void add(ReplayTick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("tick cannot be null");
        }

        entries[writeIndex] = tick;
        writeIndex = (writeIndex + 1) % entries.length;
        if (size < entries.length) {
            size++;
        }
    }

    /**
     * Returns the current buffer contents in chronological order, oldest first.
     */
    public ReplayTick[] snapshot() {
        ReplayTick[] result = new ReplayTick[size];
        if (size == 0) {
            return result;
        }

        int oldestIndex = size == entries.length ? writeIndex : 0;
        for (int i = 0; i < size; i++) {
            result[i] = entries[(oldestIndex + i) % entries.length];
        }
        return result;
    }

    public void clear() {
        for (int i = 0; i < entries.length; i++) {
            entries[i] = null;
        }
        writeIndex = 0;
        size = 0;
    }
}
