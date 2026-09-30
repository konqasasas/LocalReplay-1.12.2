package com.konqasasas.localreplay.gui.screen;

import com.konqasasas.localreplay.LocalReplayMod;
import com.konqasasas.localreplay.config.LocalReplayConfig;
import com.konqasasas.localreplay.gui.GuiContainer;
import com.konqasasas.localreplay.gui.component.Button;
import com.konqasasas.localreplay.gui.component.Label;
import com.konqasasas.localreplay.gui.component.Panel;
import com.konqasasas.localreplay.gui.component.Toggle;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;
import com.konqasasas.localreplay.replay.ReplayController;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Modern settings editor shared by Replay and Replay Library. */
public final class ReplaySettingsScreen extends GuiScreen {

    private final GuiScreen returnScreen;
    private final ReplayController replayController;
    private final GuiContainer ui = new GuiContainer();
    private LocalReplayConfig working;
    private Button replayLengthButton;
    private boolean saved;
    private UiScaleContext uiScale;
    private int uiWidth;
    private int uiHeight;

    public ReplaySettingsScreen(GuiScreen returnScreen) {
        this(returnScreen, null);
    }

    public ReplaySettingsScreen(GuiScreen returnScreen, ReplayController replayController) {
        this.returnScreen = returnScreen;
        this.replayController = replayController;
        if (replayController != null) {
            replayController.registerReplayUiScreen(this);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        if (working == null) working = copyConfig(LocalReplayMod.getConfig());
        uiScale = UiScaleContext.forScreen(mc, width, height);
        uiWidth = uiScale.getVirtualWidth();
        uiHeight = uiScale.getVirtualHeight();

        int outer = u(26);
        int pageW = Math.min(Math.max(u(660), 340), Math.max(340, uiWidth - outer * 2));
        int left = (uiWidth - pageW) / 2;
        int top = Math.max(u(24), (uiHeight - Math.max(u(330), 180)) / 2);
        int titleH = Math.max(u(42), 22);
        int gap = Math.max(2, u(14));
        int sideW = Math.max(u(190), 96);
        int mainW = Math.max(u(390), pageW - sideW - gap);
        if (mainW + sideW + gap > pageW) {
            sideW = Math.max(u(150), pageW / 3);
            mainW = pageW - sideW - gap;
        }

        int cardH = Math.min(Math.max(u(270), 150), Math.max(150, uiHeight - top - u(58)));
        int pad = u(18);
        int rowH = Math.max(u(48), 24);
        int sectionGap = Math.max(u(12), 6);
        int sectionTitleH = Math.max(u(22), 12);

        Panel root = new Panel(0, 0, uiWidth, uiHeight, LocalReplayTheme.CANVAS, LocalReplayTheme.CANVAS);
        root.setBorder(false);

        Label title = new Label(left, top, "Settings", LocalReplayTheme.TEXT_STRONG, FontRole.UI_SEMIBOLD);
        root.addChild(title);

        int cardY = top + titleH;
        Panel mainCard = new Panel(left, cardY, mainW, cardH, LocalReplayTheme.SURFACE, LocalReplayTheme.LINE);
        mainCard.setCornerRadius(Math.max(3, u(9)));
        root.addChild(mainCard);

        Panel controlsCard = new Panel(left + mainW + gap, cardY, sideW, cardH, LocalReplayTheme.SURFACE, LocalReplayTheme.LINE);
        controlsCard.setCornerRadius(Math.max(3, u(9)));
        root.addChild(controlsCard);

        int contentW = mainW - pad * 2;
        int y = pad;
        mainCard.addChild(sectionLabel(pad, y, "RECORDING"));
        y += sectionTitleH;

        mainCard.addChild(new Toggle(
                pad, y, contentW, rowH, "Recording", working.isRecording(),
                value -> working.setRecording(value)
        ));
        y += rowH;

        Label replayLengthLabel = new Label(
                pad,
                y + Math.max(1, (rowH - u(12)) / 2),
                "Replay Length",
                LocalReplayTheme.TEXT,
                FontRole.UI
        );
        mainCard.addChild(replayLengthLabel);
        int valueW = Math.max(u(112), 58);
        replayLengthButton = new Button(
                mainW - pad - valueW,
                y + Math.max(0, (rowH - Math.max(u(32), 16)) / 2),
                valueW,
                Math.max(u(32), 16),
                replayLengthText(),
                this::cycleReplayLength
        );
        mainCard.addChild(replayLengthButton);
        y += rowH + sectionGap;

        mainCard.addChild(sectionLabel(pad, y, "PLAYBACK"));
        y += sectionTitleH;
        mainCard.addChild(new Toggle(
                pad, y, contentW, rowH, "Interpolation", working.isInterpolation(),
                value -> working.setInterpolation(value)
        ));

        Label controlsTitle = new Label(pad, pad, "Controls", LocalReplayTheme.TEXT_STRONG, FontRole.UI_SEMIBOLD);
        controlsCard.addChild(controlsTitle);
        Label controlsBody = new Label(
                pad,
                pad + u(28),
                "Minecraft Controls",
                LocalReplayTheme.TEXT_MUTED,
                FontRole.UI
        );
        controlsCard.addChild(controlsBody);

        int actionH = Math.max(u(34), 18);
        int keyW = sideW - pad * 2;
        Button keyBindings = new Button(
                pad,
                pad + u(56),
                keyW,
                actionH,
                "KEY BINDINGS",
                this::openKeyBindings
        );
        controlsCard.addChild(keyBindings);

        int closeW = Math.max(u(78), 40);
        Button close = new Button(
                sideW - pad - closeW,
                cardH - pad - actionH,
                closeW,
                actionH,
                "CLOSE",
                this::saveAndClose
        );
        controlsCard.addChild(close);

        ui.setRoot(root);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) saveAndClose();
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (uiScale == null) return;
        int ux = uiScale.toDesignX(mouseX);
        int uy = uiScale.toDesignY(mouseY);
        uiScale.begin();
        try {
            Gui.drawRect(0, 0, uiWidth, uiHeight, LocalReplayTheme.CANVAS);
            ui.render(LocalReplayMod.getTrueTypeRenderer(), ux, uy, partialTicks);
        } finally {
            uiScale.end();
        }
    }

    @Override
    public void onGuiClosed() {
        if (!saved) saveConfig();
    }

    private void cycleReplayLength() {
        int[] allowed = LocalReplayConfig.getAllowedReplayLengthsSeconds();
        int current = working.getReplayLengthSeconds();
        int next = allowed[0];
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i] == current) {
                next = allowed[(i + 1) % allowed.length];
                break;
            }
        }
        working.setReplayLengthSeconds(next);
        replayLengthButton.setText(replayLengthText());
    }

    private String replayLengthText() {
        return working.getReplayLengthSeconds() + " sec";
    }

    private void openKeyBindings() {
        // Keep Local Replay key bindings in Minecraft's native Controls screen.
        // When opened from an active replay, register this exact vanilla screen
        // instance as part of the replay UI session before Minecraft switches to
        // it. Esc/Done may otherwise bypass ReplayScreen entirely.
        saveConfig();
        GuiControls controls = new GuiControls(this, mc.gameSettings);
        if (replayController != null && replayController.isActive()) {
            replayController.registerReplayUiScreen(controls);
        }
        mc.displayGuiScreen(controls);
    }

    private void saveAndClose() {
        saveConfig();
        saved = true;
        mc.displayGuiScreen(returnScreen);
    }

    private void saveConfig() {
        if (working == null) return;
        LocalReplayMod.setConfig(copyConfig(working));
        if (LocalReplayMod.getConfigManager() != null) {
            LocalReplayMod.getConfigManager().save(LocalReplayMod.getConfig());
        }
    }

    private Label sectionLabel(int x, int y, String text) {
        return new Label(x, y, text, LocalReplayTheme.TEXT_MUTED, FontRole.UI_SEMIBOLD);
    }

    private static LocalReplayConfig copyConfig(LocalReplayConfig source) {
        LocalReplayConfig copy = LocalReplayConfig.defaults();
        if (source == null) return copy;
        copy.setRecording(source.isRecording());
        copy.setReplayLengthSeconds(source.getReplayLengthSeconds());
        copy.setInterpolation(source.isInterpolation());
        // Decimal Places is intentionally no longer exposed in the UI, but
        // the config value is preserved for backward compatibility.
        copy.setDecimalPlaces(source.getDecimalPlaces());
        return copy;
    }

    private int u(int physicalPixels) {
        return UiScaleContext.fromPhysical(physicalPixels);
    }
}
