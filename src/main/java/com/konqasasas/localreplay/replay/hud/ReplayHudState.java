package com.konqasasas.localreplay.replay.hud;

import com.konqasasas.localreplay.replay.data.ReplayTick;

import java.util.Arrays;

/**
 * Deterministic HUD-facing state derived only from replay samples.
 *
 * This object deliberately contains no CYV/MPK runtime objects. Adapters may
 * format these generic values to imitate the user's normal HUD layout without
 * touching the real player or the other mod's internal state.
 */
public final class ReplayHudState {

    private final ReplayTick tick;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;
    private final int airtime;
    private final int lastAirtime;

    private final double jumpX;
    private final double jumpY;
    private final double jumpZ;
    private final float jumpYaw;
    private final float preturn;
    private final float secondTurn;

    private final double landingX;
    private final double landingY;
    private final double landingZ;

    private final double hitX;
    private final double hitY;
    private final double hitZ;
    private final float hitYaw;
    private final double hitVelocityX;
    private final double hitVelocityZ;

    private final float last45;
    private final double lastTurning;
    private final String lastInput;
    private final String lastTiming;
    private final int sidestep;
    private final int sidestepTime;
    private final int blips;
    private final int grinds;
    private final double lastBlipHeight;
    private final float[] turningAngles;

    ReplayHudState(
            ReplayTick tick,
            double velocityX,
            double velocityY,
            double velocityZ,
            int airtime,
            int lastAirtime,
            double jumpX,
            double jumpY,
            double jumpZ,
            float jumpYaw,
            float preturn,
            float secondTurn,
            double landingX,
            double landingY,
            double landingZ,
            double hitX,
            double hitY,
            double hitZ,
            float hitYaw,
            double hitVelocityX,
            double hitVelocityZ,
            float last45,
            double lastTurning,
            String lastInput,
            String lastTiming,
            int sidestep,
            int sidestepTime,
            int blips,
            int grinds,
            double lastBlipHeight,
            float[] turningAngles
    ) {
        if (tick == null) throw new IllegalArgumentException("tick cannot be null");
        this.tick = tick;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.airtime = airtime;
        this.lastAirtime = lastAirtime;
        this.jumpX = jumpX;
        this.jumpY = jumpY;
        this.jumpZ = jumpZ;
        this.jumpYaw = jumpYaw;
        this.preturn = preturn;
        this.secondTurn = secondTurn;
        this.landingX = landingX;
        this.landingY = landingY;
        this.landingZ = landingZ;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.hitYaw = hitYaw;
        this.hitVelocityX = hitVelocityX;
        this.hitVelocityZ = hitVelocityZ;
        this.last45 = last45;
        this.lastTurning = lastTurning;
        this.lastInput = lastInput == null ? "" : lastInput;
        this.lastTiming = lastTiming == null ? "" : lastTiming;
        this.sidestep = sidestep;
        this.sidestepTime = sidestepTime;
        this.blips = blips;
        this.grinds = grinds;
        this.lastBlipHeight = lastBlipHeight;
        this.turningAngles = turningAngles == null ? new float[12] : Arrays.copyOf(turningAngles, 12);
    }

    public ReplayTick getTick() { return tick; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public int getAirtime() { return airtime; }
    public int getLastAirtime() { return lastAirtime; }

    public double getJumpX() { return jumpX; }
    public double getJumpY() { return jumpY; }
    public double getJumpZ() { return jumpZ; }
    public float getJumpYaw() { return jumpYaw; }
    public float getPreturn() { return preturn; }
    public float getSecondTurn() { return secondTurn; }

    public double getLandingX() { return landingX; }
    public double getLandingY() { return landingY; }
    public double getLandingZ() { return landingZ; }

    public double getHitX() { return hitX; }
    public double getHitY() { return hitY; }
    public double getHitZ() { return hitZ; }
    public float getHitYaw() { return hitYaw; }
    public double getHitVelocityX() { return hitVelocityX; }
    public double getHitVelocityZ() { return hitVelocityZ; }

    public float getLast45() { return last45; }
    public double getLastTurning() { return lastTurning; }
    public String getLastInput() { return lastInput; }
    public String getLastTiming() { return lastTiming; }
    public int getSidestep() { return sidestep; }
    public int getSidestepTime() { return sidestepTime; }
    public int getBlips() { return blips; }
    public int getGrinds() { return grinds; }
    public double getLastBlipHeight() { return lastBlipHeight; }

    public float getTurningAngle(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= turningAngles.length) return 0.0F;
        return turningAngles[zeroBasedIndex];
    }

    public float[] copyTurningAngles() {
        return Arrays.copyOf(turningAngles, turningAngles.length);
    }
}
