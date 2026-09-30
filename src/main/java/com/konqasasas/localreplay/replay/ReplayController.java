package com.konqasasas.localreplay.replay;

import com.konqasasas.localreplay.config.LocalReplayConfig;
import com.konqasasas.localreplay.input.ReplayInputGuard;
import com.konqasasas.localreplay.replay.camera.ReplayCameraManager;
import com.konqasasas.localreplay.replay.data.ReplayData;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import com.konqasasas.localreplay.replay.playback.ReplayPose;
import com.konqasasas.localreplay.replay.record.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Central coordinator for replay playback. GUI code talks to this class instead
 * of touching camera/recorder/playback internals directly.
 */
public final class ReplayController {

    private final Minecraft minecraft;
    private final ReplayRecorder recorder;
    private final Supplier<LocalReplayConfig> configSupplier;
    private final ReplayCameraManager cameraManager;
    private final ReplayInputGuard inputGuard;
    private final Logger logger;

    private ReplayPlayback playback;
    private ReplaySource source;
    private GuiScreen returnScreen;
    private WorldClient replayWorld;
    private int replayDimension;
    private EntityPlayerSP replayPlayer;
    private boolean startedWhileDead;
    private boolean exiting;
    private final Set<GuiScreen> replayUiScreens = Collections.newSetFromMap(new IdentityHashMap<GuiScreen, Boolean>());

    public ReplayController(
            Minecraft minecraft,
            ReplayRecorder recorder,
            Supplier<LocalReplayConfig> configSupplier,
            Logger logger
    ) {
        if (minecraft == null) throw new IllegalArgumentException("minecraft cannot be null");
        if (recorder == null) throw new IllegalArgumentException("recorder cannot be null");
        if (configSupplier == null) throw new IllegalArgumentException("configSupplier cannot be null");
        this.minecraft = minecraft;
        this.recorder = recorder;
        this.configSupplier = configSupplier;
        this.cameraManager = new ReplayCameraManager(minecraft);
        this.inputGuard = new ReplayInputGuard(minecraft);
        this.logger = logger;
    }

    public boolean openLocalReplay(ReplayData data, GuiScreen returnScreen) {
        return openReplay(data, ReplaySource.LOCAL_REPLAY, returnScreen);
    }

    /**
     * Opens replay data loaded from an .lreplay file. Replay Library calls
     * this without needing to know camera/recorder internals.
     */
    public boolean openFileReplay(ReplayData data, GuiScreen returnScreen) {
        return openReplay(data, ReplaySource.FILE_REPLAY, returnScreen);
    }

    private boolean openReplay(ReplayData data, ReplaySource replaySource, GuiScreen returnScreen) {
        if (data == null || data.isEmpty() || replaySource == null
                || minecraft.world == null || minecraft.player == null) {
            return false;
        }

        exitReplay();

        replayUiScreens.clear();
        this.playback = new ReplayPlayback(data);
        this.source = replaySource;
        this.returnScreen = returnScreen;
        this.replayWorld = minecraft.world;
        this.replayPlayer = minecraft.player;
        this.replayDimension = minecraft.player.dimension;
        this.startedWhileDead = minecraft.player.isDead || minecraft.player.getHealth() <= 0.0F;

        // Opening replay is a hard input boundary: held gameplay keys/buttons
        // are released before the replay UI takes ownership.
        inputGuard.beginReplay();
        recorder.beginReplaySession();

        ReplayPose initialPose = playback.sample(0.0D, isInterpolationEnabled());
        cameraManager.activate(replayWorld, initialPose);

        logInfo("Replay opened. source=" + replaySource
                + ", ticks=" + data.size() + ", state=PAUSED, speed=1.00x");
        return true;
    }

    public void tick() {
        if (!isActive()) {
            return;
        }

        if (!isContextValid()) {
            logInfo("Replay closed because the Minecraft world/player context changed.");
            exitReplay();
            return;
        }

        if (!hasValidReplayUiOwner()) {
            logInfo("Replay closed because no registered replay UI screen owns the session.");
            exitReplay();
            return;
        }

        playback.tick();
    }


    /**
     * Runs at ClientTickEvent.START. Replay controls use raw GuiScreen keyboard
     * events, so clearing Minecraft KeyBinding state here cannot interfere with
     * Space/arrows/timeline controls, but it does prevent gameplay input from
     * leaking through to the real player.
     */
    public void updateInputIsolationBeforeClientTick() {
        inputGuard.beforeClientTick(isActive());
    }

    /**
     * Final movement-input cut, invoked after vanilla has polled KeyBindings.
     * This prevents a key held before replay opened from continuing to move the
     * real player behind the replay UI.
     */
    public void neutralizeLiveMovementInput(MovementInput input) {
        if (isActive()) {
            inputGuard.neutralizeMovementInput(input);
        }
    }

    public void updateCameraForRender(float partialTicks) {
        if (!isActive() || !cameraManager.isValidFor(minecraft.world)) {
            return;
        }
        ReplayPose pose = playback.sampleForRender(partialTicks, isInterpolationEnabled());
        cameraManager.updatePose(pose);
    }

    public void play() {
        if (playback != null) playback.play();
    }

    public void pause() {
        if (playback != null) playback.pause();
    }

    public void togglePlayPause() {
        if (playback != null) playback.togglePlayPause();
    }

    public void seek(int tick) {
        if (playback != null) playback.seek(tick);
    }

    public void stepTicks(int amount) {
        if (playback != null) playback.stepTicks(amount);
    }

    public void speedUp() {
        if (playback != null) playback.speedUp();
    }

    public void slowDown() {
        if (playback != null) playback.slowDown();
    }

    public void cycleSpeed() {
        if (playback != null) playback.cycleSpeed();
    }

    public void exitReplay() {
        if (exiting) {
            return;
        }
        exiting = true;
        try {
            boolean wasActive = isActive() || cameraManager.isActive();
            cameraManager.deactivate();
            playback = null;
            source = null;
            replayWorld = null;
            replayPlayer = null;
            replayDimension = Integer.MIN_VALUE;
            startedWhileDead = false;
            replayUiScreens.clear();
            if (recorder.isReplaySessionActive()) {
                recorder.endReplaySession();
            }
            inputGuard.endReplay();
            if (wasActive) {
                logInfo("Replay closed. Rolling recording history was preserved.");
            }
        } finally {
            exiting = false;
        }
    }

    /**
     * Marks a concrete GUI instance as belonging to the current replay UI
     * session. Identity (not class type) is used so an unrelated GuiControls
     * or mod screen can never accidentally keep a replay camera alive.
     */
    public void registerReplayUiScreen(GuiScreen screen) {
        if (screen != null && isActive()) {
            replayUiScreens.add(screen);
        }
    }

    public boolean isReplayUiScreen(GuiScreen screen) {
        return screen != null && replayUiScreens.contains(screen);
    }

    /**
     * Replay is allowed to exist only while one of the explicitly registered
     * screens owns the session. This is the lifecycle invariant used by both
     * GuiOpenEvent and the client-tick safety net.
     */
    public boolean hasValidReplayUiOwner() {
        return isActive() && isReplayUiScreen(minecraft.currentScreen);
    }

    public boolean isActive() {
        return playback != null;
    }

    public ReplayPlayback getPlayback() {
        return playback;
    }

    public ReplaySource getSource() {
        return source;
    }

    public GuiScreen getReturnScreen() {
        return returnScreen;
    }

    public boolean isContextValid() {
        if (minecraft.world == null
                || minecraft.player == null
                || minecraft.world != replayWorld
                || minecraft.player != replayPlayer
                || minecraft.player.dimension != replayDimension) {
            return false;
        }

        if (startedWhileDead) {
            return minecraft.player.isDead || minecraft.player.getHealth() <= 0.0F;
        }

        return !minecraft.player.isDead && minecraft.player.getHealth() > 0.0F;
    }

    private boolean isInterpolationEnabled() {
        LocalReplayConfig config = configSupplier.get();
        return config == null || config.isInterpolation();
    }

    private void logInfo(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }
}
