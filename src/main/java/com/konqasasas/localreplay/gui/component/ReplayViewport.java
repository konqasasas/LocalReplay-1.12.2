package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;
import com.konqasasas.localreplay.render.ReplayViewportRenderer;

/** Displays the captured Minecraft frame either fitted or at exact full-screen bounds. */
public final class ReplayViewport extends GuiElement {

    private final ReplayViewportRenderer viewportRenderer;
    private boolean frameVisible = true;

    public ReplayViewport(int x, int y, int width, int height, ReplayViewportRenderer viewportRenderer) {
        super(x, y, width, height);
        this.viewportRenderer = viewportRenderer;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();

        if (frameVisible) {
            ModernUiDraw.roundedBorder(
                    ax, ay, ax + width, ay + height, 0,
                    LocalReplayTheme.LINE_STRONG, LocalReplayTheme.SURFACE_SUBTLE
            );
            viewportRenderer.drawFitted(ax + 1, ay + 1, Math.max(0, width - 2), Math.max(0, height - 2));
        } else {
            viewportRenderer.drawExact(ax, ay, width, height);
        }
    }
    public void setFrameVisible(boolean frameVisible) {
        this.frameVisible = frameVisible;
    }
}
