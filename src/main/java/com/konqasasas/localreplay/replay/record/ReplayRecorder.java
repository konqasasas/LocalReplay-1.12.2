package com.konqasasas.localreplay.replay.record;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.config.LocalReplayConfig;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.data.ReplayKeyBindings;
import com.konqasasas.localreplay.replay.data.ReplayMetadata;
import com.konqasasas.localreplay.replay.data.ReplayTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Samples the local client player once per client tick and maintains the rolling
 * replay buffer. This class only observes client state; it never writes player
 * position, rotation, motion, input, or network state.
 *
 * Replay viewing is treated as a removable gap in recording time. If the real
 * player is still moving when a replay opens, recording continues until the
 * player has been physically stationary for several consecutive ticks. While
 * stationary, replay-viewing ticks are omitted from the ring buffer. If the
 * real player starts moving again while the replay is still open, recording
 * automatically resumes until the next stationary period.
 */
public final class ReplayRecorder {

    private static final int TICKS_PER_SECOND = 20;

    // A single near-zero motion tick is not enough (for example, the apex of a
    // jump). Require a short stable run before replay-viewing time is omitted.
    private static final int REPLAY_STATIONARY_TICKS_REQUIRED = 5;
    private static final double STATIONARY_POSITION_EPSILON = 1.0E-4D;

    private final Minecraft minecraft;
    private final Supplier<LocalReplayConfig> configSupplier;
    private final Logger logger;

    private ReplayBuffer buffer;
    private WorldClient observedWorld;
    private EntityPlayerSP observedPlayer;
    private int observedDimension = Integer.MIN_VALUE;
    private boolean deathFrozen;
    private boolean recordingWasEnabled;

    private boolean replaySessionActive;
    private boolean replayCaptureSuppressed;
    private int replayStationaryTicks;
    private ReplayTick replayLastObservedTick;
    private final List<ReplayTick> replayPendingTicks = new ArrayList<>();

    public ReplayRecorder(
            Minecraft minecraft,
            Supplier<LocalReplayConfig> configSupplier,
            Logger logger
    ) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft cannot be null");
        }
        if (configSupplier == null) {
            throw new IllegalArgumentException("configSupplier cannot be null");
        }
        this.minecraft = minecraft;
        this.configSupplier = configSupplier;
        this.logger = logger;
    }

    /**
     * Called exactly once at the end of each client tick.
     */
    public void tick() {
        if (replaySessionActive) {
            tickDuringReplay();
            return;
        }

        LocalReplayConfig config = configSupplier.get();
        if (config == null || !config.isRecording()) {
            if (recordingWasEnabled || buffer != null) {
                resetSession("recording disabled");
            }
            recordingWasEnabled = false;
            return;
        }
        recordingWasEnabled = true;

        WorldClient world = minecraft.world;
        EntityPlayerSP player = minecraft.player;

        if (world == null || player == null) {
            if (observedWorld != null || observedPlayer != null || buffer != null) {
                resetSession("world/player unavailable");
            }
            return;
        }

        int dimension = player.dimension;
        boolean contextChanged = world != observedWorld
                || player != observedPlayer
                || dimension != observedDimension;

        if (contextChanged) {
            startSession(world, player, dimension, config.getReplayLengthSeconds());
        } else {
            ensureCapacity(config.getReplayLengthSeconds());
        }

        // Keep the final pre-death buffer intact until a new player/world context
        // appears (respawn normally replaces the client player instance).
        if (player.isDead || player.getHealth() <= 0.0F) {
            deathFrozen = true;
            return;
        }

        // Defensive fallback for environments where respawn reuses the same
        // EntityPlayerSP instance instead of replacing it.
        if (deathFrozen) {
            replaceBuffer(config.getReplayLengthSeconds());
            deathFrozen = false;
            logInfo("Recording restarted after respawn.");
        }

        buffer.add(sample(player));
    }

    /**
     * Begins a replay-viewing session without discarding the existing rolling
     * history. Stable replay-viewing time is omitted, but any remaining real
     * player motion is recorded until it fully settles.
     */
    public void beginReplaySession() {
        replaySessionActive = true;
        replayStationaryTicks = 0;
        replayLastObservedTick = null;
        replayPendingTicks.clear();

        EntityPlayerSP player = minecraft.player;
        if (player == null || buffer == null || buffer.isEmpty()) {
            replayCaptureSuppressed = true;
            logInfo("Replay recording gap started with no active rolling buffer.");
            return;
        }

        // Death replays must keep the pre-death buffer frozen exactly as before.
        if (deathFrozen || player.isDead || player.getHealth() <= 0.0F) {
            replayCaptureSuppressed = true;
            logInfo("Replay recording gap started while death buffer is frozen.");
            return;
        }

        replayLastObservedTick = sample(player);
        if (hasStationaryTail(buffer, REPLAY_STATIONARY_TICKS_REQUIRED)) {
            replayCaptureSuppressed = true;
            replayStationaryTicks = REPLAY_STATIONARY_TICKS_REQUIRED;
            logInfo("Replay recording gap started immediately: player already stationary.");
        } else {
            replayCaptureSuppressed = false;
            logInfo("Replay opened while player is moving; recording will continue until stationary.");
        }
    }

    /**
     * Ends replay viewing and resumes normal rolling recording on the same
     * buffer. The omitted stationary viewing interval is intentionally not
     * backfilled.
     */
    public void endReplaySession() {
        if (!replaySessionActive) {
            return;
        }

        int discardedPendingTicks = replayPendingTicks.size();
        replayPendingTicks.clear();
        replaySessionActive = false;
        replayCaptureSuppressed = false;
        replayStationaryTicks = 0;
        replayLastObservedTick = null;

        EntityPlayerSP player = minecraft.player;
        if (deathFrozen
                && player != null
                && (player.isDead || player.getHealth() <= 0.0F)
                && buffer != null
                && !buffer.isEmpty()) {
            logInfo("Death replay closed; preserving pre-death buffer until respawn.");
            return;
        }

        if (discardedPendingTicks > 0) {
            logInfo("Replay closed; discarded " + discardedPendingTicks
                    + " pending stationary replay-viewing ticks.");
        }
        logInfo("Replay closed; rolling recording buffer preserved.");
    }

    /**
     * Legacy names retained for source compatibility with older Local Replay
     * code. New code should use beginReplaySession/endReplaySession.
     */
    public void suspendForReplay() {
        beginReplaySession();
    }

    public void resumeAfterReplay() {
        endReplaySession();
    }

    public boolean isSuspendedForReplay() {
        return replaySessionActive && replayCaptureSuppressed;
    }

    public boolean isReplaySessionActive() {
        return replaySessionActive;
    }

    public boolean isReplayCaptureSuppressed() {
        return replaySessionActive && replayCaptureSuppressed;
    }

    public ReplayData createSnapshot() {
        if (buffer == null || buffer.isEmpty()) {
            return null;
        }

        ReplayTick[] ticks = buffer.snapshot();
        ReplayMetadata metadata = new ReplayMetadata(
                ReplayMetadata.FORMAT_VERSION_CURRENT,
                LocalReplayMod.MINECRAFT_VERSION,
                LocalReplayMod.VERSION,
                System.currentTimeMillis(),
                ticks.length,
                captureKeyBindings()
        );
        return new ReplayData(metadata, ticks);
    }

    public int getBufferedTickCount() {
        return buffer == null ? 0 : buffer.size();
    }

    public int getCapacityTicks() {
        return buffer == null ? 0 : buffer.capacity();
    }

    public boolean isDeathFrozen() {
        return deathFrozen;
    }

    public void clear() {
        replaySessionActive = false;
        replayCaptureSuppressed = false;
        replayStationaryTicks = 0;
        replayLastObservedTick = null;
        replayPendingTicks.clear();
        resetSession("manual clear");
    }

    private void tickDuringReplay() {
        LocalReplayConfig config = configSupplier.get();
        if (config == null || !config.isRecording()) {
            // Match the old replay behavior: do not mutate the buffer merely
            // because Recording was toggled from a child settings screen. The
            // normal recorder path will apply that setting after replay closes.
            return;
        }

        WorldClient world = minecraft.world;
        EntityPlayerSP player = minecraft.player;
        if (world == null || player == null
                || world != observedWorld
                || player != observedPlayer
                || player.dimension != observedDimension) {
            // ReplayController owns context invalidation/cleanup. Do not start a
            // new recording session underneath an active replay.
            return;
        }

        if (player.isDead || player.getHealth() <= 0.0F) {
            deathFrozen = true;
            replayCaptureSuppressed = true;
            replayLastObservedTick = null;
            replayStationaryTicks = 0;
            replayPendingTicks.clear();
            return;
        }

        if (buffer == null) {
            replayPendingTicks.clear();
            return;
        }

        // Replay-length changes are intentionally deferred until normal
        // recording resumes. Applying them here would reset the ring buffer
        // while the user is merely viewing a replay.
        ReplayTick current = sample(player);

        if (replayCaptureSuppressed) {
            // Stable replay-viewing time is never inserted into the rolling
            // buffer. Keep observing the real player so externally-caused
            // motion (physics, knockback, water, flying, etc.) can resume
            // capture from the first actual movement tick.
            if (replayLastObservedTick != null
                    && !isStationaryTransition(replayLastObservedTick, current)) {
                replayCaptureSuppressed = false;
                replayStationaryTicks = 0;
                replayPendingTicks.clear();
                replayPendingTicks.add(current);
                commitReplayPendingTicks();
                logInfo("Real player moved during replay; recording resumed until stationary.");
            }
            replayLastObservedTick = current;
            return;
        }

        // Do not write replay-session samples straight into the ring buffer.
        // Stationary-looking ticks remain provisional until either movement
        // resumes (then they are part of a real continuous motion interval) or
        // the stationary threshold is reached (then the entire confirmation
        // tail is discarded as replay-viewing time).
        replayPendingTicks.add(current);

        if (replayLastObservedTick != null
                && isStationaryTransition(replayLastObservedTick, current)) {
            replayStationaryTicks++;
            if (replayStationaryTicks >= REPLAY_STATIONARY_TICKS_REQUIRED) {
                int discarded = replayPendingTicks.size();
                replayPendingTicks.clear();
                replayCaptureSuppressed = true;
                logInfo("Real player is stationary; discarded " + discarded
                        + " pending confirmation ticks and omitted replay-viewing time.");
            }
        } else {
            // A definite movement transition proves every pending sample since
            // the last commit belongs to the real movement timeline. This also
            // preserves short pauses that never reached the stationary threshold.
            replayStationaryTicks = 0;
            commitReplayPendingTicks();
        }

        replayLastObservedTick = current;
    }

    private void startSession(
            WorldClient world,
            EntityPlayerSP player,
            int dimension,
            int replayLengthSeconds
    ) {
        observedWorld = world;
        observedPlayer = player;
        observedDimension = dimension;
        deathFrozen = false;
        replaceBuffer(replayLengthSeconds);
        logInfo("Recording started. dimension=" + dimension
                + ", capacity=" + buffer.capacity() + " ticks");
    }

    private void ensureCapacity(int replayLengthSeconds) {
        int desiredCapacity = capacityForSeconds(replayLengthSeconds);
        if (buffer == null || buffer.capacity() != desiredCapacity) {
            replaceBuffer(replayLengthSeconds);
            deathFrozen = false;
            replayStationaryTicks = 0;
            replayLastObservedTick = null;
            replayPendingTicks.clear();
            logInfo("Replay length changed. Buffer reset to " + desiredCapacity + " ticks.");
        }
    }

    private void replaceBuffer(int replayLengthSeconds) {
        buffer = new ReplayBuffer(capacityForSeconds(replayLengthSeconds));
    }

    private void resetSession(String reason) {
        if (buffer != null && !buffer.isEmpty()) {
            logInfo("Recording buffer cleared (" + reason + "). ticks=" + buffer.size());
        }
        buffer = null;
        observedWorld = null;
        observedPlayer = null;
        observedDimension = Integer.MIN_VALUE;
        deathFrozen = false;
        replayStationaryTicks = 0;
        replayLastObservedTick = null;
        replayPendingTicks.clear();
    }

    private void commitReplayPendingTicks() {
        if (replayPendingTicks.isEmpty()) {
            return;
        }
        if (buffer == null) {
            replayPendingTicks.clear();
            return;
        }

        for (ReplayTick tick : replayPendingTicks) {
            buffer.add(tick);
        }
        replayPendingTicks.clear();
    }

    private ReplayTick sample(EntityPlayerSP player) {
        boolean acceptGameplayKeys = minecraft.currentScreen == null;
        GameSettings settings = minecraft.gameSettings;

        byte inputFlags = ReplayTick.buildInputFlags(
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindForward),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindLeft),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindBack),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindRight),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindJump),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindSprint),
                acceptGameplayKeys && GameSettings.isKeyDown(settings.keyBindSneak)
        );

        byte stateFlags = ReplayTick.buildStateFlags(
                player.onGround,
                player.capabilities != null && player.capabilities.isFlying,
                player.isSprinting()
        );

        return new ReplayTick(
                player.posX,
                player.posY,
                player.posZ,
                player.motionX,
                player.motionY,
                player.motionZ,
                player.rotationYaw,
                player.rotationPitch,
                inputFlags,
                stateFlags
        );
    }

    private ReplayKeyBindings captureKeyBindings() {
        GameSettings settings = minecraft.gameSettings;
        return new ReplayKeyBindings(
                safeKeyName(settings.keyBindForward),
                safeKeyName(settings.keyBindLeft),
                safeKeyName(settings.keyBindBack),
                safeKeyName(settings.keyBindRight),
                safeKeyName(settings.keyBindJump),
                safeKeyName(settings.keyBindSprint),
                safeKeyName(settings.keyBindSneak)
        );
    }

    private static boolean hasStationaryTail(ReplayBuffer source, int requiredTicks) {
        ReplayTick[] ticks = source.snapshot();
        if (ticks.length < requiredTicks) {
            return false;
        }

        int start = ticks.length - requiredTicks;
        for (int i = start + 1; i < ticks.length; i++) {
            if (!isStationaryTransition(ticks[i - 1], ticks[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStationaryTransition(ReplayTick previous, ReplayTick current) {
        if (previous == null || current == null) {
            return false;
        }

        /*
         * For replay-gap compression, actual displacement is the source of
         * truth. EntityPlayerSP motion is not: while visually standing still,
         * vanilla/Forge can leave a small vertical or collision-related motion
         * value at the END tick sample. Treating that bookkeeping velocity as
         * real movement caused replay-viewing ticks to be committed into the
         * rolling buffer even though the player's recorded position never moved.
         *
         * Airborne/knockback motion is still preserved because it produces real
         * position deltas. A one-tick apex/brief pause also remains safe because
         * five consecutive stationary transitions are required before omission.
         */
        return Math.abs(current.getX() - previous.getX()) <= STATIONARY_POSITION_EPSILON
                && Math.abs(current.getY() - previous.getY()) <= STATIONARY_POSITION_EPSILON
                && Math.abs(current.getZ() - previous.getZ()) <= STATIONARY_POSITION_EPSILON;
    }

    private static String safeKeyName(net.minecraft.client.settings.KeyBinding binding) {
        String displayName = binding.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            return "UNKNOWN";
        }
        return displayName.trim();
    }

    private static int capacityForSeconds(int seconds) {
        return Math.multiplyExact(seconds, TICKS_PER_SECOND);
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }
}
