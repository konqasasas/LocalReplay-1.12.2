package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;

import java.util.function.BooleanSupplier;

/** Simple non-interactive keystroke indicator. */
public final class KeystrokeKey extends GuiElement {

    private final String text;
    private final BooleanSupplier pressedSupplier;

    public KeystrokeKey(int x, int y, int width, int height, String text, BooleanSupplier pressedSupplier) {
        super(x, y, width, height);
        this.text = text == null ? "" : text;
        this.pressedSupplier = pressedSupplier;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        boolean pressed = pressedSupplier != null && pressedSupplier.getAsBoolean();
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        int bg = pressed ? LocalReplayTheme.CONTROL_HOVER : LocalReplayTheme.CONTROL;
        int border = pressed ? LocalReplayTheme.LINE_CONTROL : LocalReplayTheme.LINE;
        int textColor = pressed ? LocalReplayTheme.TEXT_STRONG : LocalReplayTheme.TEXT_MUTED;

        ModernUiDraw.roundedBorder(ax, ay, ax + width, ay + height, Math.max(2, height / 5),
                border, bg);

        textRenderer.drawCenteredString(
                text,
                ax + width * 0.5F,
                ay + height * 0.5F,
                textColor,
                FontRole.UI
        );
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return false;
    }
}
