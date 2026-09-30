package com.konqasasas.localreplay.replay.hud.mpk2;

import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import com.konqasasas.localreplay.replay.hud.ReplayHudTimeline;

/**
 * MPKMod2-specific tick state reconstructed only from generic Local Replay data.
 *
 * No MPKMod2 classes or runtime objects are referenced. This mirrors the parts
 * of MPKMod2 Player.buildAndSave() that can be derived deterministically from
 * position, rotation, motion, movement keys and generic player state.
 */
final class Mpk2ReplayTimeline {

    private final State[] states;

    Mpk2ReplayTimeline(ReplayData data) {
        if (data == null) throw new IllegalArgumentException("data cannot be null");
        this.states = build(data);
    }

    State getState(int index) {
        if (states.length == 0) return null;
        int clamped = Math.max(0, Math.min(index, states.length - 1));
        return states[clamped];
    }

    private static State[] build(ReplayData data) {
        State[] out = new State[data.size()];
        if (out.length == 0) return out;

        for (int i = 0; i < data.size(); i++) {
            ReplayTick cur = data.getTick(i);
            if (i == 0) {
                out[i] = State.initial(cur);
                continue;
            }

            ReplayTick prevTick = data.getTick(i - 1);
            State prev = out[i - 1];
            State prevPrev = i >= 2 ? out[i - 2] : null;

            int airtime;
            if (prevTick.isOnGround() || cur.isFlying()) airtime = prev.airtime;
            else airtime = prev.airtime + 1;
            if (prevTick.isOnGround() && !cur.isOnGround()) airtime = 1;

            int jumpCooldown = prev.jumpCooldown;
            boolean jumpTick = false;
            if (cur.isJumpDown()) {
                if (jumpCooldown > 1) {
                    jumpCooldown--;
                } else if (!cur.isOnGround() && prevTick.isOnGround()) {
                    jumpTick = true;
                    jumpCooldown = 10;
                }
            } else {
                jumpCooldown = 0;
            }

            boolean landTick = !prevTick.isOnGround() && cur.isOnGround();

            PosAndAngle lastLanding = landTick
                    ? new PosAndAngle(prevTick.getX(), prevTick.getY(), prevTick.getZ(), cur.getYaw(), cur.getPitch())
                    : prev.lastLanding;
            PosAndAngle lastHit = prev.landTick
                    ? new PosAndAngle(prevTick.getX(), prevTick.getY(), prevTick.getZ(), cur.getYaw(), cur.getPitch())
                    : prev.lastHit;
            PosAndAngle lastJump = jumpTick
                    ? new PosAndAngle(prevTick.getX(), prevTick.getY(), prevTick.getZ(), cur.getYaw(), cur.getPitch())
                    : prev.lastJump;

            Float deltaYaw = cur.getYaw() - prevTick.getYaw();
            if (deltaYaw.floatValue() == 0.0F) deltaYaw = null;
            Float deltaPitch = cur.getPitch() - prevTick.getPitch();
            if (deltaPitch.floatValue() == 0.0F) deltaPitch = null;

            Float last45 = prev.last45;
            if (prev.jumpTick && !isMovingSideways(prevTick) && isMovingSideways(cur)) {
                last45 = deltaYaw;
            }

            int grinds = prev.grinds;
            boolean grindTick = jumpTick
                    && cur.getY() == prevTick.getY()
                    && cur.getMotionY() == (-0.08D * 0.98F);
            if (grindTick) {
                grinds = prevPrev != null && prevPrev.grindTick ? prev.grinds + 1 : 1;
            }

            Blip lastBlip = prev.lastBlip;
            if (cur.isOnGround()
                    && !prevTick.isOnGround()
                    && cur.getY() == prevTick.getY()
                    && !prev.jumpTick) {
                lastBlip = lastBlip == null
                        ? new Blip(1, 1, cur.getX(), cur.getY(), cur.getZ())
                        : new Blip(lastBlip.chainedBlips + 1, lastBlip.chainedBlips + 1,
                                cur.getX(), cur.getY(), cur.getZ());
            } else if (cur.isOnGround() && lastBlip != null) {
                lastBlip = new Blip(0, lastBlip.lastChainedBlips,
                        lastBlip.x, lastBlip.y, lastBlip.z);
            }

            int sidestep = prev.sidestep;
            boolean wadStart = prev.wadStart;
            if (jumpTick) {
                wadStart = !isMovingSideways(cur);
                if (wadStart) {
                    if (!isMovingSideways(prevTick)) sidestep = -1;
                } else {
                    sidestep = hasSwappedSidewaysDirection(prevTick, cur) ? 0 : -1;
                }
            } else if (wadStart && isMovingSideways(cur) && prev.airtime != airtime) {
                sidestep = prev.airtime;
                wadStart = false;
            }

            Float preturn = null;
            if (prevPrev != null) {
                preturn = jumpTick ? prevPrev.deltaYaw : prev.preturn;
            }

            out[i] = new State(
                    cur,
                    airtime,
                    jumpCooldown,
                    jumpTick,
                    landTick,
                    grindTick,
                    grinds,
                    lastLanding,
                    lastHit,
                    lastJump,
                    lastBlip,
                    deltaYaw,
                    deltaPitch,
                    last45,
                    preturn,
                    sidestep,
                    wadStart
            );
        }
        return out;
    }

    private static boolean isMovingSideways(ReplayTick tick) {
        return tick.isLeftDown() ^ tick.isRightDown();
    }

    private static int sidewaysDirection(ReplayTick tick) {
        int direction = 0;
        if (tick.isLeftDown()) direction--;
        if (tick.isRightDown()) direction++;
        return direction;
    }

    private static boolean hasSwappedSidewaysDirection(ReplayTick before, ReplayTick after) {
        int beforeDirection = sidewaysDirection(before);
        return beforeDirection != 0 && beforeDirection == -sidewaysDirection(after);
    }

    static final class State {
        private final ReplayTick tick;
        private final int airtime;
        private final int jumpCooldown;
        private final boolean jumpTick;
        private final boolean landTick;
        private final boolean grindTick;
        private final int grinds;
        private final PosAndAngle lastLanding;
        private final PosAndAngle lastHit;
        private final PosAndAngle lastJump;
        private final Blip lastBlip;
        private final Float deltaYaw;
        private final Float deltaPitch;
        private final Float last45;
        private final Float preturn;
        private final int sidestep;
        private final boolean wadStart;

        State(
                ReplayTick tick,
                int airtime,
                int jumpCooldown,
                boolean jumpTick,
                boolean landTick,
                boolean grindTick,
                int grinds,
                PosAndAngle lastLanding,
                PosAndAngle lastHit,
                PosAndAngle lastJump,
                Blip lastBlip,
                Float deltaYaw,
                Float deltaPitch,
                Float last45,
                Float preturn,
                int sidestep,
                boolean wadStart
        ) {
            this.tick = tick;
            this.airtime = airtime;
            this.jumpCooldown = jumpCooldown;
            this.jumpTick = jumpTick;
            this.landTick = landTick;
            this.grindTick = grindTick;
            this.grinds = grinds;
            this.lastLanding = lastLanding;
            this.lastHit = lastHit;
            this.lastJump = lastJump;
            this.lastBlip = lastBlip;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.last45 = last45;
            this.preturn = preturn;
            this.sidestep = sidestep;
            this.wadStart = wadStart;
        }

        static State initial(ReplayTick tick) {
            return new State(tick, 0, 0, false, false, false, 0,
                    null, null, null, null, null, null, null, null, -1, false);
        }

        ReplayTick getTick() { return tick; }
        int getAirtime() { return airtime; }
        boolean isJumpTick() { return jumpTick; }
        int getGrinds() { return grinds; }
        PosAndAngle getLastLanding() { return lastLanding; }
        PosAndAngle getLastHit() { return lastHit; }
        PosAndAngle getLastJump() { return lastJump; }
        Blip getLastBlip() { return lastBlip; }
        float getDeltaYaw() { return deltaYaw == null ? 0.0F : deltaYaw.floatValue(); }
        float getDeltaPitch() { return deltaPitch == null ? 0.0F : deltaPitch.floatValue(); }
        Float getLast45() { return last45; }
        float getPreturn() { return preturn == null ? 0.0F : preturn.floatValue(); }

        String getLastSidestep() {
            switch (sidestep) {
                case -1: return "None";
                case 0: return "WDWA";
                case 1: return "WAD";
                default: return "WAD " + sidestep + "t";
            }
        }
    }

    static final class PosAndAngle {
        final double x;
        final double y;
        final double z;
        final float trueYaw;
        final float truePitch;
        final float yaw;
        final float pitch;

        PosAndAngle(double x, double y, double z, float trueYaw, float truePitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.trueYaw = trueYaw;
            this.truePitch = truePitch;
            this.yaw = ReplayHudTimeline.wrapYaw(trueYaw);
            this.pitch = ReplayHudTimeline.wrapYaw(truePitch);
        }
    }

    static final class Blip {
        final int chainedBlips;
        final int lastChainedBlips;
        final double x;
        final double y;
        final double z;

        Blip(int chainedBlips, int lastChainedBlips, double x, double y, double z) {
            this.chainedBlips = chainedBlips;
            this.lastChainedBlips = lastChainedBlips;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
