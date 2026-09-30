package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;

public class Button extends GuiElement {

    public enum Style {
        DEFAULT,
        PRIMARY,
        DANGER,
        GHOST
    }

    private String text;
    private Runnable onClick;
    private boolean pressed;
    private Style style = Style.DEFAULT;

    public Button(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height);
        this.text = text == null ? "" : text;
        this.onClick = onClick;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        boolean activePointer = enabled && (hovered || pressed);

        int background;
        int border;
        switch (style) {
            case PRIMARY:
                background = activePointer ? LocalReplayTheme.CONTROL_PRIMARY_HOVER : LocalReplayTheme.CONTROL_PRIMARY;
                border = activePointer ? LocalReplayTheme.FOCUS : LocalReplayTheme.FOCUS_SOFT;
                break;
            case DANGER:
                background = activePointer ? LocalReplayTheme.CONTROL_DANGER_HOVER : LocalReplayTheme.CONTROL;
                border = activePointer ? 0xFF79454B : LocalReplayTheme.LINE_STRONG;
                break;
            case GHOST:
                background = activePointer ? LocalReplayTheme.CONTROL_HOVER : 0x00151A20;
                border = activePointer ? LocalReplayTheme.LINE_CONTROL : 0x002B343F;
                break;
            case DEFAULT:
            default:
                background = pressed ? LocalReplayTheme.CONTROL_PRESSED
                        : activePointer ? LocalReplayTheme.CONTROL_HOVER : LocalReplayTheme.CONTROL;
                border = activePointer ? LocalReplayTheme.LINE_CONTROL : LocalReplayTheme.LINE_STRONG;
                break;
        }

        int textColor = enabled ? LocalReplayTheme.TEXT_STRONG : LocalReplayTheme.TEXT_FAINT;
        int radius = Math.max(2, Math.min(5, height / 5));
        if (style != Style.GHOST || activePointer) {
            ModernUiDraw.roundedBorder(
                    ax,
                    ay,
                    ax + width,
                    ay + height,
                    radius,
                    applyOpacity(border),
                    applyOpacity(background)
            );
        }

        textRenderer.drawCenteredString(
                text,
                ax + width * 0.5F,
                ay + height * 0.5F,
                applyOpacity(style == Style.GHOST && !activePointer ? LocalReplayTheme.TEXT_MUTED : textColor),
                style == Style.PRIMARY ? FontRole.UI_SEMIBOLD : FontRole.UI
        );
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (visible && enabled && mouseButton == 0 && contains(mouseX, mouseY)) {
            // No latched focus outline. Pointer state is the complete visual
            // affordance, matching the existing ZIP27+ behavior.
            focused = false;
            pressed = true;
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean handled = super.mouseReleased(mouseX, mouseY, state);
        if (state == 0 && pressed) {
            pressed = false;
            return true;
        }
        return handled;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }

    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    public void setStyle(Style style) {
        this.style = style == null ? Style.DEFAULT : style;
    }

    public Style getStyle() {
        return style;
    }
}
