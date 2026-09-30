package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;

import java.util.function.Consumer;

/** Modern compact right-aligned switch for Settings rows. */
public class Toggle extends GuiElement {

    private String label;
    private boolean value;
    private Consumer<Boolean> listener;

    public Toggle(int x, int y, int width, int height, String label, boolean value, Consumer<Boolean> listener) {
        super(x, y, width, height);
        this.label = label == null ? "" : label;
        this.value = value;
        this.listener = listener;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        int lineHeight = textRenderer.getLineHeight(FontRole.UI);
        int textY = ay + Math.max(0, (height - lineHeight) / 2);
        int textColor = enabled ? LocalReplayTheme.TEXT : LocalReplayTheme.TEXT_FAINT;
        textRenderer.drawString(label, ax, textY, applyOpacity(textColor), FontRole.UI);

        int switchH = Math.max(10, Math.min(height - 4, 18));
        int switchW = Math.max(20, (int) Math.round(switchH * 1.75D));
        int sx = ax + width - switchW;
        int sy = ay + (height - switchH) / 2;
        int radius = switchH / 2;
        int track = value ? 0xFF275F9F : 0xFF303A44;
        int border = hovered && enabled ? LocalReplayTheme.FOCUS_SOFT : (value ? 0xFF4F86C7 : 0xFF44505C);
        ModernUiDraw.roundedBorder(sx, sy, sx + switchW, sy + switchH, radius,
                applyOpacity(border), applyOpacity(track));

        int knob = Math.max(6, switchH - 6);
        int knobX = value ? sx + switchW - knob - 3 : sx + 3;
        int knobY = sy + (switchH - knob) / 2;
        ModernUiDraw.roundedRect(knobX, knobY, knobX + knob, knobY + knob, knob / 2,
                applyOpacity(value ? 0xFFFFFFFF : 0xFFA6B0B8));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (visible && enabled && mouseButton == 0 && contains(mouseX, mouseY)) {
            value = !value;
            if (listener != null) listener.accept(value);
            return true;
        }
        return false;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}
