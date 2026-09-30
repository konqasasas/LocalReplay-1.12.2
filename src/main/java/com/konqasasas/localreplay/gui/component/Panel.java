package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;

public class Panel extends GuiElement {

    private int backgroundColor;
    private int borderColor;
    private boolean border = true;
    private int cornerRadius;

    public Panel(int x, int y, int width, int height, int backgroundColor, int borderColor) {
        super(x, y, width, height);
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        if (cornerRadius > 1) {
            if (border && width > 1 && height > 1) {
                ModernUiDraw.roundedBorder(ax, ay, ax + width, ay + height, cornerRadius,
                        applyOpacity(borderColor), applyOpacity(backgroundColor));
            } else {
                ModernUiDraw.roundedRect(ax, ay, ax + width, ay + height, cornerRadius, applyOpacity(backgroundColor));
            }
            return;
        }

        if (border && width > 1 && height > 1) {
            ModernUiDraw.roundedBorder(
                    ax,
                    ay,
                    ax + width,
                    ay + height,
                    0,
                    applyOpacity(borderColor),
                    applyOpacity(backgroundColor)
            );
        } else {
            ModernUiDraw.roundedRect(ax, ay, ax + width, ay + height, 0, applyOpacity(backgroundColor));
        }
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public void setBorder(boolean border) {
        this.border = border;
    }

    public void setCornerRadius(int cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
    }
}
