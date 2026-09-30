package com.konqasasas.localreplay.gui;

import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;

public final class GuiContainer {

    private GuiElement root;

    public void setRoot(GuiElement root) {
        this.root = root;
    }

    public GuiElement getRoot() {
        return root;
    }

    public void render(TrueTypeRenderer renderer, int mouseX, int mouseY, float partialTicks) {
        if (root != null) {
            root.render(renderer, mouseX, mouseY, partialTicks);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return root != null && root.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        return root != null && root.mouseReleased(mouseX, mouseY, state);
    }
}
