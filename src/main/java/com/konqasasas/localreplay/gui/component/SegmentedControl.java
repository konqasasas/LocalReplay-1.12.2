package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;

/** A compact shared-background segmented control. */
public final class SegmentedControl extends GuiElement {

    private final String[] labels;
    private final int[] widths;
    private final Runnable[] actions;
    private final int primaryIndex;
    private int pressedIndex = -1;

    public SegmentedControl(
            int x,
            int y,
            int height,
            String[] labels,
            int[] widths,
            Runnable[] actions,
            int primaryIndex
    ) {
        super(x, y, sum(widths), height);
        if (labels == null || widths == null || actions == null
                || labels.length != widths.length || labels.length != actions.length
                || labels.length == 0) {
            throw new IllegalArgumentException("Segment arrays must be non-empty and have matching lengths");
        }
        this.labels = labels.clone();
        this.widths = widths.clone();
        this.actions = actions.clone();
        this.primaryIndex = primaryIndex;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer tr, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        int radius = Math.max(3, UiScaleContext.fromPhysical(7));

        ModernUiDraw.roundedBorder(
                ax, ay, ax + width, ay + height, radius,
                applyOpacity(LocalReplayTheme.LINE),
                applyOpacity(LocalReplayTheme.CONTROL)
        );

        int cursor = ax;
        for (int i = 0; i < labels.length; i++) {
            int segmentW = widths[i];
            boolean segmentHover = enabled && mouseX >= cursor && mouseX < cursor + segmentW
                    && mouseY >= ay && mouseY < ay + height;
            boolean primary = i == primaryIndex;

            if (primary) {
                int inset = UiScaleContext.fromPhysical(2);
                ModernUiDraw.roundedRect(
                        cursor + inset,
                        ay + inset,
                        cursor + segmentW - inset,
                        ay + height - inset,
                        Math.max(2, radius - inset),
                        applyOpacity(segmentHover || pressedIndex == i
                                ? LocalReplayTheme.CONTROL_PRIMARY_HOVER
                                : LocalReplayTheme.CONTROL_ACTIVE)
                );
            } else if (segmentHover || pressedIndex == i) {
                int inset = UiScaleContext.fromPhysical(1);
                ModernUiDraw.roundedRect(
                        cursor + inset,
                        ay + inset,
                        cursor + segmentW - inset,
                        ay + height - inset,
                        Math.max(2, radius - inset),
                        applyOpacity(LocalReplayTheme.CONTROL_HOVER)
                );
            }

            if (i > 0) {
                ModernUiDraw.verticalLine(
                        cursor,
                        ay + UiScaleContext.fromPhysical(6),
                        ay + height - UiScaleContext.fromPhysical(6),
                        applyOpacity(LocalReplayTheme.LINE)
                );
            }

            FontRole role = primary ? FontRole.UI_SEMIBOLD : FontRole.MONO;
            int color = enabled
                    ? (primary ? LocalReplayTheme.TEXT_STRONG : LocalReplayTheme.TEXT)
                    : LocalReplayTheme.TEXT_FAINT;
            tr.drawCenteredString(labels[i], cursor + segmentW * 0.5F, ay + height * 0.5F,
                    applyOpacity(color), role);
            cursor += segmentW;
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (!visible || !enabled || mouseButton != 0 || !contains(mouseX, mouseY)) return false;
        int index = segmentAt(mouseX);
        if (index >= 0) {
            pressedIndex = index;
            Runnable action = actions[index];
            if (action != null) action.run();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean handled = super.mouseReleased(mouseX, mouseY, state);
        if (state == 0 && pressedIndex >= 0) {
            pressedIndex = -1;
            return true;
        }
        return handled;
    }

    public void setSegmentText(int index, String text) {
        if (index >= 0 && index < labels.length) labels[index] = text == null ? "" : text;
    }

    private int segmentAt(int mouseX) {
        int local = mouseX - getAbsoluteX();
        int cursor = 0;
        for (int i = 0; i < widths.length; i++) {
            cursor += widths[i];
            if (local < cursor) return i;
        }
        return -1;
    }

    private static int sum(int[] values) {
        int total = 0;
        if (values != null) for (int value : values) total += Math.max(1, value);
        return total;
    }
}
