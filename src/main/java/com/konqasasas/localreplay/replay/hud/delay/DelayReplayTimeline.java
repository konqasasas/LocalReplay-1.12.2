package com.konqasasas.localreplay.replay.hud.delay;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayTick;

/**
 * Deterministically reconstructs Delay Mod's grounded-delay counter from the
 * recorded onGround flag. This mirrors DelayState.onClientTick() exactly:
 * airborne ticks retain the previous value, landing starts at zero, and each
 * subsequent continuously-grounded tick increments the value by one.
 */
public final class DelayReplayTimeline {

    private final int[] groundTicks;

    public DelayReplayTimeline(ReplayData data) {
        if (data == null) throw new IllegalArgumentException("data cannot be null");

        this.groundTicks = new int[data.size()];
        int value = 0;
        boolean wasOnGround = false;

        for (int i = 0; i < data.size(); i++) {
            ReplayTick tick = data.getTick(i);
            boolean nowOnGround = tick.isOnGround();
            boolean prevOnGround = wasOnGround;
            boolean isAir = !prevOnGround || !nowOnGround;
            boolean isGround = !isAir;
            boolean groundStarted = !prevOnGround && nowOnGround;

            if (groundStarted) {
                value = 0;
            }
            if (isGround) {
                value++;
            }

            groundTicks[i] = value;
            wasOnGround = nowOnGround;
        }
    }

    public int getGroundTicks(int tickIndex) {
        if (groundTicks.length == 0) return 0;
        if (tickIndex <= 0) return groundTicks[0];
        if (tickIndex >= groundTicks.length) return groundTicks[groundTicks.length - 1];
        return groundTicks[tickIndex];
    }
}
