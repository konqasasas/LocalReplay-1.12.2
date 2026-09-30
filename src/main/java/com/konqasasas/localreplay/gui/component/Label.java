package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;

public class Label extends GuiElement {

    private String text;
    private int color;
    private FontRole role;
    private boolean centered;

    public Label(int x, int y, String text, int color, FontRole role) {
        super(x, y, 0, 0);
        this.text = text == null ? "" : text;
        this.color = color;
        this.role = role == null ? FontRole.UI : role;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int drawColor = applyOpacity(enabled ? color : 0xFF747E86);
        if (centered && width > 0 && height > 0) {
            textRenderer.drawCenteredString(
                    text,
                    getAbsoluteX() + width * 0.5F,
                    getAbsoluteY() + height * 0.5F,
                    drawColor,
                    role
            );
        } else {
            textRenderer.drawString(text, getAbsoluteX(), getAbsoluteY(), drawColor, role);
        }
    }

    @Override
    public boolean contains(int mouseX, int mouseY) {
        return false;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setRole(FontRole role) {
        this.role = role == null ? FontRole.UI : role;
    }

    public void setCentered(boolean centered) {
        this.centered = centered;
    }
}
