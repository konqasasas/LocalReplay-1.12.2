package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;

public class Tooltip extends GuiElement {

    private String text;

    public Tooltip(String text) {
        super(0, 0, 0, 0);
        this.text = text == null ? "" : text;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        if (text.isEmpty()) {
            return;
        }
        int w = textRenderer.getStringWidth(text, FontRole.UI) + 12;
        int h = textRenderer.getLineHeight(FontRole.UI) + 8;
        int ax = mouseX + 10;
        int ay = mouseY + 10;
        ModernUiDraw.roundedBorder(
                ax, ay, ax + w, ay + h, Math.max(2, h / 5),
                LocalReplayTheme.LINE_STRONG, LocalReplayTheme.SURFACE
        );
        textRenderer.drawString(text, ax + 6, ay + 4, LocalReplayTheme.TEXT, FontRole.UI);
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return false;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }
}
