package com.konqasasas.localreplay.replay.playback;

import com.konqasasas.localreplay.replay.ReplayState;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import net.minecraft.util.math.MathHelper;

/**
 * Pure replay timeline logic. Playback time is driven by a monotonic real-time
 * clock rather than the 20 Hz client tick so the camera and timeline can move
 * at the actual render frame rate. Player/world state is never modified here.
 */
public final class ReplayPlayback {

    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double[] SPEED_PRESETS = {
            0.05D,
            0.10D,
            0.25D,
            0.50D,
            1.00D,
            1.50D,
            2.00D
    };

    private final ReplayData data;
    /** Exact base position at {@link #baseTimeNanos}. */
    private double playhead;
    private long baseTimeNanos;
    private ReplayState state = ReplayState.PAUSED;
    private int speedIndex = 4; // 1.00x

    public ReplayPlayback(ReplayData data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Replay data cannot be null or empty");
        }
        this.data = data;
        this.baseTimeNanos = System.nanoTime();
    }

    public ReplayData getData() {
        return data;
    }

    public ReplayState getState() {
        updateEndState(System.nanoTime());
        return state;
    }

    public boolean isPlaying() {
        updateEndState(System.nanoTime());
        return state == ReplayState.PLAYING;
    }

    public boolean isPaused() {
        return !isPlaying();
    }

    /** Current fractional replay tick, smooth at render-frame frequency. */
    public double getPlayhead() {
        return currentPlayhead(System.nanoTime());
    }

    public int getCurrentTickIndex() {
        return clampTick((int) Math.floor(getPlayhead()));
    }

    public int getLastTickIndex() {
        return data.size() - 1;
    }

    public double getSpeed() {
        return SPEED_PRESETS[speedIndex];
    }

    /** partialTicks is intentionally ignored; the monotonic clock is smoother. */
    public double getRenderPlayhead(float partialTicks) {
        return getPlayhead();
    }

    public int getSpeedIndex() {
        return speedIndex;
    }

    public void play() {
        long now = System.nanoTime();
        double current = currentPlayhead(now);
        if (current >= getLastTickIndex()) {
            current = 0.0D;
        }
        playhead = current;
        baseTimeNanos = now;
        state = ReplayState.PLAYING;
    }

    public void pause() {
        long now = System.nanoTime();
        double current = currentPlayhead(now);
        state = ReplayState.PAUSED;
        // Analysis mode: paused positions are always exact recorded ticks.
        playhead = clampPlayhead(Math.floor(current));
        baseTimeNanos = now;
    }

    public void togglePlayPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    /**
     * Client ticks no longer advance playback. This only commits the current
     * real-time position so end-of-replay state changes are noticed even on a
     * frame where no UI query occurs.
     */
    public void tick() {
        updateEndState(System.nanoTime());
    }

    public void seek(int tick) {
        state = ReplayState.PAUSED;
        playhead = clampTick(tick);
        baseTimeNanos = System.nanoTime();
    }

    public void stepTicks(int amount) {
        long now = System.nanoTime();
        double current = currentPlayhead(now);
        state = ReplayState.PAUSED;
        int base = (int) Math.floor(current);
        playhead = clampTick(base + amount);
        baseTimeNanos = now;
    }

    public void speedUp() {
        if (speedIndex >= SPEED_PRESETS.length - 1) {
            return;
        }
        synchronizeBase(System.nanoTime());
        speedIndex++;
    }

    public void slowDown() {
        if (speedIndex <= 0) {
            return;
        }
        synchronizeBase(System.nanoTime());
        speedIndex--;
    }

    /** Cycle forward through the fixed replay speed presets, wrapping at 2.00x. */
    public void cycleSpeed() {
        synchronizeBase(System.nanoTime());
        speedIndex = (speedIndex + 1) % SPEED_PRESETS.length;
    }

    public ReplayPose sampleForRender(float partialTicks, boolean interpolation) {
        return sample(getPlayhead(), interpolation);
    }

    public ReplayPose sample(double requestedPlayhead, boolean interpolation) {
        double p = clampPlayhead(requestedPlayhead);
        int fromIndex = clampTick((int) Math.floor(p));
        ReplayTick from = data.getTick(fromIndex);

        if (!interpolation || fromIndex >= getLastTickIndex()) {
            return poseOf(from);
        }

        int toIndex = fromIndex + 1;
        ReplayTick to = data.getTick(toIndex);
        double fraction = p - fromIndex;

        double x = lerp(from.getX(), to.getX(), fraction);
        double y = lerp(from.getY(), to.getY(), fraction);
        double z = lerp(from.getZ(), to.getZ(), fraction);
        float yaw = lerpAngle(from.getYaw(), to.getYaw(), (float) fraction);
        float pitch = lerpAngle(from.getPitch(), to.getPitch(), (float) fraction);

        return new ReplayPose(x, y, z, yaw, pitch);
    }

    public ReplayTick getDisplayedTick() {
        return data.getTick(getCurrentTickIndex());
    }

    private double currentPlayhead(long now) {
        if (state != ReplayState.PLAYING) {
            return clampPlayhead(playhead);
        }
        double elapsedSeconds = Math.max(0L, now - baseTimeNanos) / 1_000_000_000.0D;
        double value = playhead + elapsedSeconds * TICKS_PER_SECOND * getSpeed();
        if (value >= getLastTickIndex()) {
            return getLastTickIndex();
        }
        return clampPlayhead(value);
    }

    private void synchronizeBase(long now) {
        playhead = currentPlayhead(now);
        baseTimeNanos = now;
    }

    private void updateEndState(long now) {
        if (state != ReplayState.PLAYING) {
            return;
        }
        double current = currentPlayhead(now);
        if (current >= getLastTickIndex()) {
            playhead = getLastTickIndex();
            baseTimeNanos = now;
            state = ReplayState.PAUSED;
        }
    }

    private ReplayPose poseOf(ReplayTick tick) {
        return new ReplayPose(
                tick.getX(),
                tick.getY(),
                tick.getZ(),
                tick.getYaw(),
                tick.getPitch()
        );
    }

    private int clampTick(int tick) {
        return MathHelper.clamp(tick, 0, getLastTickIndex());
    }

    private double clampPlayhead(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        double max = getLastTickIndex();
        if (value > max) {
            return max;
        }
        return value;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = MathHelper.wrapDegrees(b - a);
        return a + delta * t;
    }
}
