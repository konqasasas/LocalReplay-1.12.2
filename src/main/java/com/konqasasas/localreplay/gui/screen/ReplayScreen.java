package com.konqasasas.localreplay.gui.screen;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.gui.GuiContainer;
import com.konqasasas.localreplay.gui.component.Button;
import com.konqasasas.localreplay.gui.component.Label;
import com.konqasasas.localreplay.gui.component.Panel;
import com.konqasasas.localreplay.gui.component.ReplayViewport;
import com.konqasasas.localreplay.gui.component.SegmentedControl;
import com.konqasasas.localreplay.gui.component.Timeline;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;
import com.konqasasas.localreplay.replay.ReplayController;
import com.konqasasas.localreplay.replay.hud.ReplayHudManager;
import com.konqasasas.localreplay.replay.io.ReplayFileException;
import com.konqasasas.localreplay.replay.io.ReplayFileIO;
import com.konqasasas.localreplay.replay.playback.ReplayPlayback;
import com.konqasasas.localreplay.render.ReplayDebugOverlayRenderer;
import com.konqasasas.localreplay.render.ReplayViewportRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Full-screen Local Replay viewer with a stable physical-density UI overlay. */
public final class ReplayScreen extends GuiScreen {

    private final ReplayController controller;
    private final GuiContainer ui = new GuiContainer();
    private final ReplayViewportRenderer viewportRenderer;
    private final ReplayDebugOverlayRenderer debugOverlayRenderer;
    private ReplayHudManager replayHudManager;

    private UiScaleContext uiScale;
    private int uiWidth;
    private int uiHeight;

    private ReplayViewport viewport;
    private Panel controlOverlay;
    private Timeline timeline;
    private SegmentedControl transportControl;
    private SegmentedControl speedControl;
    private Button saveButton;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private boolean closing;
    private boolean navigatingToChild;
    private long saveStatusUntilMillis;
    private String saveStatusText;

    public ReplayScreen(ReplayController controller) {
        if (controller == null) throw new IllegalArgumentException("controller cannot be null");
        this.controller = controller;
        controller.registerReplayUiScreen(this);
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        this.viewportRenderer = new ReplayViewportRenderer(minecraft);
        this.debugOverlayRenderer = new ReplayDebugOverlayRenderer(minecraft);
    }

    @Override
    public void initGui() {
        super.initGui();

        uiScale = UiScaleContext.forScreen(mc, width, height);
        uiWidth = uiScale.getVirtualWidth();
        uiHeight = uiScale.getVirtualHeight();
        TrueTypeRenderer tr = LocalReplayMod.getTrueTypeRenderer();

        // The camera/world always uses Minecraft's native GUI coordinate space.
        viewport = new ReplayViewport(0, 0, width, height, viewportRenderer);
        viewport.setFrameVisible(false);

        // Dock follows the HTML proportions: centered, capped, and always two rows.
        int sideMargin = u(28);
        int maxDockWidth = u(1180);
        int availableWidth = Math.max(1, uiWidth - sideMargin * 2);
        int dockWidth = Math.min(maxDockWidth, availableWidth);
        int dockPadX = u(16);
        int dockPadY = u(12);
        int rowGap = u(10);
        int topRowH = u(34);
        int bottomRowH = u(28);
        int dockHeight = dockPadY * 2 + topRowH + rowGap + bottomRowH;
        int dockX = (uiWidth - dockWidth) / 2;
        int dockY = Math.max(u(12), uiHeight - dockHeight - u(22));

        Panel root = new Panel(0, 0, uiWidth, uiHeight, 0x00000000, 0x00000000);
        root.setBorder(false);

        controlOverlay = new Panel(
                dockX, dockY, dockWidth, dockHeight,
                LocalReplayTheme.OVERLAY_SURFACE,
                LocalReplayTheme.LINE
        );
        controlOverlay.setCornerRadius(u(10));
        root.addChild(controlOverlay);

        int contentX = dockPadX;
        int contentW = dockWidth - dockPadX * 2;
        int topY = dockPadY;

        ControlMetrics m = controlMetrics(tr);
        int groupGap = u(18);
        int actionGap = u(6);

        int[] transportWidths = {
                m.step100Width, m.step10Width, m.step1Width,
                m.playWidth,
                m.step1Width, m.step10Width, m.step100Width
        };
        // Top row is laid out as three independent zones:
        // speed on the left, transport pinned to the exact horizontal center,
        // and secondary actions on the right.  The transport position must not
        // drift when the side controls change width.
        int transportWidth = 0;
        for (int value : transportWidths) transportWidth += value;
        int transportX = (dockWidth - transportWidth) / 2;
        transportControl = new SegmentedControl(
                transportX, topY, topRowH,
                new String[]{"-100", "-10", "-1", "▶", "+1", "+10", "+100"},
                transportWidths,
                new Runnable[]{
                        () -> controller.stepTicks(-100),
                        () -> controller.stepTicks(-10),
                        () -> controller.stepTicks(-1),
                        controller::togglePlayPause,
                        () -> controller.stepTicks(1),
                        () -> controller.stepTicks(10),
                        () -> controller.stepTicks(100)
                },
                3
        );
        controlOverlay.addChild(transportControl);

        int speedX = contentX;
        speedControl = new SegmentedControl(
                speedX, topY, topRowH,
                new String[]{"-", "1.00x", "+"},
                new int[]{m.speedSmallWidth, m.speedValueWidth, m.speedSmallWidth},
                new Runnable[]{controller::slowDown, controller::cycleSpeed, controller::speedUp},
                -1
        );
        controlOverlay.addChild(speedControl);

        Button settingsButton = new Button(0, topY, m.settingsWidth, topRowH, "Settings", this::openSettings);
        settingsButton.setStyle(Button.Style.GHOST);
        Button closeButton = new Button(0, topY, m.closeWidth, topRowH, "Close", this::closeToReturnScreen);
        closeButton.setStyle(Button.Style.GHOST);
        saveButton = new Button(0, topY, m.saveWidth, topRowH, "Save", this::saveReplay);
        saveButton.setStyle(Button.Style.GHOST);

        int actionsWidth = m.settingsWidth + m.saveWidth + m.closeWidth + actionGap * 2;
        int actionsX = contentX + contentW - actionsWidth;
        settingsButton.setBounds(actionsX, topY, m.settingsWidth, topRowH);
        saveButton.setBounds(actionsX + m.settingsWidth + actionGap, topY, m.saveWidth, topRowH);
        closeButton.setBounds(actionsX + m.settingsWidth + actionGap + m.saveWidth + actionGap,
                topY, m.closeWidth, topRowH);
        controlOverlay.addChild(settingsButton);
        controlOverlay.addChild(saveButton);
        controlOverlay.addChild(closeButton);

        // Preserve the exact centered transport position. On unusually narrow
        // windows only the secondary action buttons are compacted; the main
        // replay transport is never shifted away from the screen center.
        int minGap = u(12);
        int transportLeft = transportX;
        int transportRight = transportX + transportControl.getWidth();
        int speedRight = speedX + speedControl.getWidth();
        if (speedRight + minGap > transportLeft) {
            // The virtual coordinate system already keeps physical density stable.
            // There is intentionally no transport shift here; very small windows
            // simply use the minimum speed-control metrics.
            speedX = contentX;
            speedControl.setBounds(speedX, topY, speedControl.getWidth(), topRowH);
        }

        if (transportRight + minGap > actionsX) {
            int availableForActions = Math.max(0, contentX + contentW - transportRight - minGap);
            int minimumActions = tr.getStringWidth("Settings", FontRole.UI) + u(14)
                    + tr.getStringWidth("Save", FontRole.UI) + u(14)
                    + tr.getStringWidth("Close", FontRole.UI) + u(14)
                    + actionGap * 2;
            if (availableForActions >= minimumActions) {
                int extra = availableForActions - minimumActions;
                m.settingsWidth = tr.getStringWidth("Settings", FontRole.UI) + u(14) + extra / 3;
                m.saveWidth = tr.getStringWidth("Save", FontRole.UI) + u(14) + extra / 3;
                m.closeWidth = availableForActions - m.settingsWidth - m.saveWidth - actionGap * 2;
                actionsWidth = m.settingsWidth + m.saveWidth + m.closeWidth + actionGap * 2;
                actionsX = contentX + contentW - actionsWidth;
                settingsButton.setBounds(actionsX, topY, m.settingsWidth, topRowH);
                saveButton.setBounds(actionsX + m.settingsWidth + actionGap, topY, m.saveWidth, topRowH);
                closeButton.setBounds(actionsX + m.settingsWidth + actionGap + m.saveWidth + actionGap,
                        topY, m.closeWidth, topRowH);
            }
        }

        int bottomY = dockPadY + topRowH + rowGap;
        int timeBoxW = Math.max(u(92), tr.getStringWidth("00:00.000", FontRole.MONO_LARGE) + u(10));
        int timelineGap = u(12);

        currentTimeLabel = new Label(contentX, bottomY, "00:00.000", LocalReplayTheme.TEXT_STRONG, FontRole.MONO_LARGE);
        currentTimeLabel.setBounds(contentX, bottomY, timeBoxW, bottomRowH);
        currentTimeLabel.setCentered(true);
        controlOverlay.addChild(currentTimeLabel);

        totalTimeLabel = new Label(contentX + contentW - timeBoxW, bottomY,
                "00:00.000", LocalReplayTheme.TEXT_MUTED, FontRole.MONO_LARGE);
        totalTimeLabel.setBounds(contentX + contentW - timeBoxW, bottomY, timeBoxW, bottomRowH);
        totalTimeLabel.setCentered(true);
        controlOverlay.addChild(totalTimeLabel);

        int timelineX = contentX + timeBoxW + timelineGap;
        int timelineW = Math.max(u(180), contentW - timeBoxW * 2 - timelineGap * 2);
        timeline = new Timeline(
                timelineX, bottomY, timelineW, bottomRowH,
                partial -> playback() == null ? 0.0D : playback().getRenderPlayhead((float) partial),
                () -> playback() == null ? 0 : playback().getLastTickIndex(),
                controller::seek
        );
        controlOverlay.addChild(timeline);

        ui.setRoot(root);

        ReplayPlayback activePlayback = playback();
        if (replayHudManager == null && activePlayback != null) {
            replayHudManager = new ReplayHudManager(mc, activePlayback.getData(), LocalReplayMod.getLogger());
        }
    }

    private ControlMetrics controlMetrics(TrueTypeRenderer tr) {
        ControlMetrics m = new ControlMetrics();
        m.step100Width = buttonWidth(tr, "-100", 48, 14, FontRole.MONO);
        m.step10Width = buttonWidth(tr, "-10", 43, 14, FontRole.MONO);
        m.step1Width = buttonWidth(tr, "-1", 38, 12, FontRole.MONO);
        m.playWidth = buttonWidth(tr, "▶", 52, 18, FontRole.UI_SEMIBOLD);
        m.speedSmallWidth = buttonWidth(tr, "+", 36, 12, FontRole.MONO);
        m.speedValueWidth = buttonWidth(tr, "1.00x", 72, 18, FontRole.MONO);
        m.settingsWidth = buttonWidth(tr, "Settings", 84, 18, FontRole.UI);
        m.saveWidth = buttonWidth(tr, "Saved", 68, 18, FontRole.UI);
        m.closeWidth = buttonWidth(tr, "Close", 72, 18, FontRole.UI);
        return m;
    }

    private int buttonWidth(TrueTypeRenderer tr, String text, int minPhysicalWidth,
                            int horizontalPaddingPhysical, FontRole role) {
        return Math.max(u(minPhysicalWidth), tr.getStringWidth(text, role) + u(horizontalPaddingPhysical));
    }

    private static final class ControlMetrics {
        private int step100Width, step10Width, step1Width, playWidth;
        private int speedSmallWidth, speedValueWidth;
        private int settingsWidth, saveWidth, closeWidth;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!controller.isActive()) {
            closeToReturnScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeToReturnScreen();
            return;
        }
        if (keyCode == Keyboard.KEY_SPACE) {
            controller.togglePlayPause();
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
            int amount = stepAmount();
            controller.stepTicks(keyCode == Keyboard.KEY_LEFT ? -amount : amount);
            return;
        }
        if (keyCode == Keyboard.KEY_MINUS || keyCode == Keyboard.KEY_SUBTRACT) {
            controller.slowDown();
            return;
        }
        if (keyCode == Keyboard.KEY_EQUALS || keyCode == Keyboard.KEY_ADD) {
            controller.speedUp();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (uiScale == null) return;
        ui.mouseClicked(uiScale.toDesignX(mouseX), uiScale.toDesignY(mouseY), mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (uiScale == null) return;
        ui.mouseReleased(uiScale.toDesignX(mouseX), uiScale.toDesignY(mouseY), state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || !controller.isActive() || uiScale == null) return;

        int mx = uiScale.toDesignX(mouseX());
        int my = uiScale.toDesignY(mouseY());
        boolean overControls = controlOverlay != null && controlOverlay.contains(mx, my);
        boolean overTimeline = timeline != null && timeline.contains(mx, my);
        if (!overControls || overTimeline) {
            int amount = stepAmount();
            controller.stepTicks(wheel > 0 ? amount : -amount);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ReplayPlayback playback = playback();
        if (playback == null) return;

        int uiMouseX = uiScale == null ? mouseX : uiScale.toDesignX(mouseX);
        int uiMouseY = uiScale == null ? mouseY : uiScale.toDesignY(mouseY);
        if (timeline != null && Mouse.isButtonDown(0)) timeline.mouseDragged(uiMouseX);
        updateControlState(playback);

        // Keep the proven Replay rendering path completely in Minecraft GUI coordinates.
        viewportRenderer.captureWorldFrame();
        viewport.render(LocalReplayMod.getTrueTypeRenderer(), mouseX, mouseY, partialTicks);
        debugOverlayRenderer.renderFullScreen();
        if (replayHudManager != null && replayHudManager.hasActiveAdapters()) {
            replayHudManager.render(playback, 0, 0, width, height, partialTicks);
        }

        String replayMarker = "REPLAY";
        mc.fontRenderer.drawString(
                replayMarker,
                width / 2 - mc.fontRenderer.getStringWidth(replayMarker) / 2,
                height / 5,
                0xFFFF0000,
                false
        );

        if (uiScale != null) {
            uiScale.begin();
            try {
                ui.render(LocalReplayMod.getTrueTypeRenderer(), uiMouseX, uiMouseY, partialTicks);
            } finally {
                uiScale.end();
            }
        }
    }

    private void updateControlState(ReplayPlayback playback) {
        if (transportControl != null) transportControl.setSegmentText(3, playback.isPlaying() ? "Ⅱ" : "▶");
        if (speedControl != null) speedControl.setSegmentText(1, String.format(Locale.ROOT, "%.2fx", playback.getSpeed()));
        if (currentTimeLabel != null) currentTimeLabel.setText(formatTime(playback.getPlayhead()));
        if (totalTimeLabel != null) totalTimeLabel.setText(formatTime(playback.getData().size()));

        if (saveButton != null) {
            if (saveStatusText != null && System.currentTimeMillis() < saveStatusUntilMillis) {
                saveButton.setText(titleCase(saveStatusText));
                saveButton.setStyle("ERROR".equals(saveStatusText) ? Button.Style.DANGER : Button.Style.GHOST);
            } else {
                saveStatusText = null;
                saveButton.setText("Save");
                saveButton.setStyle(Button.Style.GHOST);
            }
        }
    }

    private void saveReplay() {
        ReplayPlayback activePlayback = playback();
        if (activePlayback == null || LocalReplayMod.getPaths() == null) {
            setSaveStatus("ERROR");
            return;
        }
        try {
            Path saved = ReplayFileIO.saveAutoNamed(
                    LocalReplayMod.getPaths().getReplaysDirectory(), activePlayback.getData());
            setSaveStatus("SAVED");
            if (LocalReplayMod.getLogger() != null) {
                LocalReplayMod.getLogger().info("Replay saved: {}", saved.toAbsolutePath());
            }
        } catch (ReplayFileException exception) {
            setSaveStatus("ERROR");
            if (LocalReplayMod.getLogger() != null) {
                LocalReplayMod.getLogger().error("Failed to save replay.", exception);
            }
        }
    }

    private void setSaveStatus(String text) {
        saveStatusText = text;
        saveStatusUntilMillis = System.currentTimeMillis() + 1500L;
    }

    private ReplayPlayback playback() {
        return controller.getPlayback();
    }

    @Override
    public void onGuiClosed() {
        viewportRenderer.close();
        if (navigatingToChild) {
            navigatingToChild = false;
            return;
        }
        if (!closing && controller.isActive()) controller.exitReplay();
    }

    private void openSettings() {
        if (!controller.isActive()) return;
        controller.pause();
        navigatingToChild = true;
        mc.displayGuiScreen(new ReplaySettingsScreen(this, controller));
    }

    public void closeToReturnScreen() {
        if (closing) return;
        closing = true;
        GuiScreen returnScreen = controller.getReturnScreen();
        controller.exitReplay();
        mc.displayGuiScreen(returnScreen);
    }

    private int stepAmount() {
        if (isShiftKeyDown()) return 100;
        if (isCtrlKeyDown()) return 10;
        return 1;
    }

    private int u(int physicalPixels) {
        return UiScaleContext.fromPhysical(physicalPixels);
    }

    private int mouseX() {
        return Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
    }

    private int mouseY() {
        return height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
    }

    private static String formatTime(double tick) {
        long millis = Math.max(0L, Math.round(tick * 50.0D));
        long hours = millis / 3_600_000L;
        long minutes = (millis / 60_000L) % 60L;
        long seconds = (millis / 1_000L) % 60L;
        long ms = millis % 1_000L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        }
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
