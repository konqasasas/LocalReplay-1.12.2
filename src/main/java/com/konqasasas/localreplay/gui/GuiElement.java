package com.konqasasas.localreplay.gui;

import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal retained-mode UI node. Coordinates are relative to the parent.
 */
public abstract class GuiElement {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean hovered;
    protected boolean focused;
    protected float opacity = 1.0F;

    private GuiElement parent;
    private final List<GuiElement> children = new ArrayList<>();

    protected GuiElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public final void render(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = contains(mouseX, mouseY);
        drawSelf(textRenderer, mouseX, mouseY, partialTicks);
        for (GuiElement child : children) {
            child.render(textRenderer, mouseX, mouseY, partialTicks);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!visible || !enabled) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (!visible) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseReleased(mouseX, mouseY, state)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(int mouseX, int mouseY) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        return mouseX >= ax && mouseY >= ay && mouseX < ax + width && mouseY < ay + height;
    }

    public void addChild(GuiElement child) {
        if (child == null) {
            return;
        }
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(GuiElement child) {
        if (children.remove(child) && child != null) {
            child.parent = null;
        }
    }

    public List<GuiElement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public int getAbsoluteX() {
        return parent == null ? x : parent.getAbsoluteX() + x;
    }

    public int getAbsoluteY() {
        return parent == null ? y : parent.getAbsoluteY() + y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public GuiElement getParent() {
        return parent;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHovered() {
        return hovered;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        if (Float.isNaN(opacity)) {
            this.opacity = 1.0F;
        } else {
            this.opacity = Math.max(0.0F, Math.min(1.0F, opacity));
        }
    }

    protected float getEffectiveOpacity() {
        float own = Math.max(0.0F, Math.min(1.0F, opacity));
        if (parent == null) {
            return own;
        }
        return own * parent.getEffectiveOpacity();
    }

    protected int applyOpacity(int color) {
        int alpha = color >>> 24 & 0xFF;
        int adjusted = Math.max(0, Math.min(255, Math.round(alpha * getEffectiveOpacity())));
        return (adjusted << 24) | (color & 0x00FFFFFF);
    }

    protected abstract void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks);
}
