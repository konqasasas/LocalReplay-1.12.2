package com.konqasasas.localreplay.replay.hud;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayTick;

/**
 * Precomputes seek-safe, deterministic HUD state for every replay tick.
 *
 * The state machine intentionally mirrors only generic/tick-based parts of the
 * parkour HUD behavior. Millisecond timing, landing-block PB/offset state and
 * other mod-runtime-only data are not represented here.
 */
public final class ReplayHudTimeline {

    private final ReplayHudState[] states;

    public ReplayHudTimeline(ReplayData data) {
        if (data == null) throw new IllegalArgumentException("data cannot be null");
        this.states = build(data);
    }

    public ReplayHudState getState(int index) {
        if (states.length == 0) return null;
        int clamped = Math.max(0, Math.min(index, states.length - 1));
        return states[clamped];
    }

    public int size() {
        return states.length;
    }

    private static ReplayHudState[] build(ReplayData data) {
        ReplayHudState[] out = new ReplayHudState[data.size()];
        if (out.length == 0) return out;

        Mutable m = new Mutable();
        TimingMachine timing = new TimingMachine();
        ReplayTick first = data.getTick(0);
        ReplayTick prev = first;
        ReplayTick prev2 = first;
        double prevVy = 0.0D;
        int airtime = 0;
        int lastAirtime = 0;

        for (int i = 0; i < data.size(); i++) {
            ReplayTick cur = data.getTick(i);

            // CYV evaluates timing before refreshing this tick's derived motion
            // and airtime. Using the previous derived Y velocity reproduces the
            // same tick ordering without reading live mod state.
            timing.advance(cur, prev, prev2, airtime, prevVy);

            if ((!prev.isOnGround() || !cur.isOnGround()) && !cur.isFlying()) {
                airtime++;
            }

            double vx = i == 0 ? 0.0D : cur.getX() - prev.getX();
            double vy = i == 0 ? 0.0D : cur.getY() - prev.getY();
            double vz = i == 0 ? 0.0D : cur.getZ() - prev.getZ();
            float turn = i == 0 ? 0.0F : cur.getYaw() - prev.getYaw();

            if (airtime == 1 && cur.isJumpDown() && vy >= 0.0D) {
                m.jumpX = cur.getX();
                m.jumpY = cur.getY();
                m.jumpZ = cur.getZ();
                m.jumpYaw = cur.getYaw();
                m.preturn = i >= 2 ? wrapYaw(prev.getYaw() - prev2.getYaw()) : 0.0F;

                if (cur.getY() == prev.getY() && vy == 0.0D) {
                    if (!m.grindStarted) {
                        m.grindStarted = true;
                        m.grinds = 0;
                    }
                    m.grinds++;
                } else if (!m.grindStarted) {
                    m.grinds = 0;
                }

                ReplayHudState previousState = i > 0 ? out[i - 1] : null;
                int previousAirtime = previousState == null ? 0 : previousState.getAirtime();
                double previousVelocityY = i >= 2
                        ? prev.getY() - prev2.getY()
                        : 0.0D;
                if (prev.isOnGround()
                        && i >= 2
                        && !prev2.isOnGround()
                        && previousVelocityY == 0.0D
                        && (prev.getY() % 0.015625D != 0.0D)
                        && previousAirtime > 1) {
                    m.blips++;
                    m.lastBlipHeight = prev.getY();
                } else {
                    m.blips = 0;
                }
            } else if (airtime == 2 && prevVy > 0.0D) {
                m.secondTurn = wrapYaw(cur.getYaw() - m.jumpYaw);
            }

            boolean prevForward = prev.isForwardDown();
            boolean prevLeft = prev.isLeftDown();
            boolean prevRight = prev.isRightDown();
            boolean currentStrafe = cur.isLeftDown() != cur.isRightDown();
            boolean currentForward = cur.isForwardDown() != cur.isBackDown();
            if (prevForward
                    && ((prevLeft && prevRight) || (!prevLeft && !prevRight))
                    && currentStrafe
                    && currentForward
                    && !cur.isOnGround()) {
                m.last45 = turn;
            }

            if (i > 0 && cur.getYaw() != prev.getYaw()) {
                m.lastTurning = turn;
            }

            if (airtime >= 1 && airtime <= m.turningAngles.length) {
                m.turningAngles[airtime - 1] = turn;
            }

            if (i > 0 && cur.isOnGround() && !prev.isOnGround() && vy < 0.0D) {
                m.landingX = prev.getX();
                m.landingY = prev.getY();
                m.landingZ = prev.getZ();
                m.hitX = cur.getX();
                m.hitY = cur.getY();
                m.hitZ = cur.getZ();
                m.hitYaw = cur.getYaw();
                m.hitVelocityX = vx;
                m.hitVelocityZ = vz;
            }

            m.lastInput = movementString(cur);

            // CYV stores the pre-reset airtime inside lastTick, then resets the
            // global counter on ground. Preserve both views for later derived
            // events such as blip detection.
            int sampledAirtime = airtime;
            if (cur.isOnGround()) {
                if (airtime != 0) lastAirtime = airtime;
                airtime = 0;
                m.grindStarted = false;
            } else {
                lastAirtime = airtime;
            }

            out[i] = new ReplayHudState(
                    cur,
                    vx,
                    vy,
                    vz,
                    sampledAirtime,
                    lastAirtime,
                    m.jumpX,
                    m.jumpY,
                    m.jumpZ,
                    m.jumpYaw,
                    m.preturn,
                    m.secondTurn,
                    m.landingX,
                    m.landingY,
                    m.landingZ,
                    m.hitX,
                    m.hitY,
                    m.hitZ,
                    m.hitYaw,
                    m.hitVelocityX,
                    m.hitVelocityZ,
                    m.last45,
                    m.lastTurning,
                    m.lastInput,
                    timing.lastTiming,
                    timing.sidestep,
                    timing.sidestepTime,
                    m.blips,
                    m.grinds,
                    m.lastBlipHeight,
                    m.turningAngles
            );

            prev2 = prev;
            prev = cur;
            prevVy = vy;
        }
        return out;
    }

    private static String movementString(ReplayTick tick) {
        StringBuilder s = new StringBuilder(4);
        if (tick.isForwardDown()) s.append('W');
        if (tick.isLeftDown()) s.append('A');
        if (tick.isBackDown()) s.append('S');
        if (tick.isRightDown()) s.append('D');
        return s.toString();
    }

    public static float wrapYaw(float yaw) {
        float facing = yaw % 360.0F;
        if (facing > 180.0F) facing -= 360.0F;
        else if (facing < -180.0F) facing += 360.0F;
        return facing;
    }

    private static int strafe(ReplayTick tick) {
        int value = 0;
        if (tick.isLeftDown()) value--;
        if (tick.isRightDown()) value++;
        return value;
    }

    private static final class Mutable {
        double jumpX;
        double jumpY;
        double jumpZ;
        float jumpYaw;
        float preturn;
        float secondTurn;
        double landingX;
        double landingY;
        double landingZ;
        double hitX;
        double hitY;
        double hitZ;
        float hitYaw;
        double hitVelocityX;
        double hitVelocityZ;
        float last45;
        double lastTurning;
        String lastInput = "";
        int blips;
        int grinds;
        boolean grindStarted;
        double lastBlipHeight;
        final float[] turningAngles = new float[12];
    }

    /** Tick-only subset of CYV 1.12 timing/sidestep logic. */
    private static final class TimingMachine {
        int lastJumpTime = -1;
        int lastGroundMoveTime = -1;
        int lastMoveTime = -1;
        int lastSprintTime = -1;
        int lastSneakTime = -2;
        boolean locked;
        String lastTiming = "";
        int sidestep = 0;
        int sidestepTime = -1;

        void advance(ReplayTick cur, ReplayTick prev, ReplayTick prev2, int airtime, double previousVy) {
            boolean anyMove = cur.isForwardDown() || cur.isBackDown() || cur.isLeftDown() || cur.isRightDown();
            if (anyMove) {
                lastMoveTime++;
                lastGroundMoveTime++;

                if (lastJumpTime > -1
                        && lastMoveTime == 0
                        && airtime != 0
                        && !(previousVy == 0.0D && prev.isOnGround())
                        && (lastTiming.contains("Pessi") || !locked)) {
                    lastTiming = (lastJumpTime + 1 == 1)
                            ? "Max Pessi"
                            : "Pessi " + (lastJumpTime + 1) + " ticks";
                    locked = true;
                }

                if (prev.isOnGround() && !prev2.isOnGround()) {
                    lastGroundMoveTime = 0;
                }
            } else {
                lastMoveTime = -1;
                lastGroundMoveTime = -1;
            }

            if (cur.isJumpDown() && airtime == 0) {
                lastJumpTime = 0;

                if ((lastGroundMoveTime == 0 || lastMoveTime == 0) && !locked) {
                    lastTiming = "Jam";
                    if (cur.isSprintDown() || !cur.isForwardDown()) {
                        locked = true;
                    }
                } else if (lastGroundMoveTime > -1 && !locked && lastJumpTime == 0) {
                    if (lastSneakTime == -1) {
                        lastTiming = "Burst " + lastGroundMoveTime + " ticks";
                    } else if (lastSneakTime > -1) {
                        lastTiming = "Burstjam " + lastGroundMoveTime + " ticks";
                    } else {
                        lastTiming = "HH " + lastGroundMoveTime + " ticks";
                    }
                    locked = true;
                }
            } else if (!prev.isOnGround() && lastJumpTime > -1) {
                lastJumpTime++;
            } else {
                lastJumpTime = -1;
            }

            if (cur.isSneakDown()) {
                if (lastSneakTime == -2) lastSneakTime = 0;
                else lastSneakTime++;
            } else {
                if (lastSneakTime == -1 || lastSneakTime == -2) lastSneakTime = -2;
                else lastSneakTime = -1;
            }

            if ((cur.isSprintDown() || lastSprintTime != -1) && !prev.isOnGround()) {
                lastSprintTime++;
                if (lastTiming.startsWith("Jam")
                        && lastSprintTime == 0
                        && !locked
                        && prev.isForwardDown()
                        && lastJumpTime >= 1) {
                    lastTiming = lastJumpTime == 1 ? "Max FMM" : "FMM " + lastJumpTime + " ticks";
                    locked = true;
                }
            } else {
                lastSprintTime = -1;
            }

            if (!cur.isForwardDown()
                    && !cur.isBackDown()
                    && !cur.isLeftDown()
                    && !cur.isRightDown()
                    && !cur.isJumpDown()
                    && cur.isOnGround()) {
                locked = false;
            }

            int curStrafe = strafe(cur);
            int prevStrafe = strafe(prev);
            if (cur.isJumpDown() && airtime == 0) {
                if (prevStrafe != 0 && curStrafe == 0) {
                    sidestepTime = 1;
                    sidestep = 0;
                } else if (curStrafe != 0) {
                    sidestepTime = 1;
                    sidestep = 1;
                } else {
                    sidestep = -1;
                    sidestepTime = 0;
                }
            } else if (airtime > 0) {
                if (sidestep == -1 && curStrafe != 0) {
                    sidestep = 0;
                    sidestepTime = airtime;
                }
                if (sidestepTime == airtime && curStrafe == 0 && sidestep == 0) {
                    sidestepTime++;
                }
            }

            if (lastJumpTime > 999) lastJumpTime = 999;
            if (lastGroundMoveTime > 999) lastGroundMoveTime = 999;
            if (lastMoveTime > 999) lastMoveTime = 999;
            if (lastSprintTime > 999) lastSprintTime = 999;
        }
    }
}
