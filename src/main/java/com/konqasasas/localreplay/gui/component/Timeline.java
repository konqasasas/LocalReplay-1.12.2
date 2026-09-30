package com.konqasasas.localreplay.gui.component;

import com.konqasasas.localreplay.gui.GuiElement;
import com.konqasasas.localreplay.gui.font.FontRole;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.gui.theme.LocalReplayTheme;
import com.konqasasas.localreplay.gui.theme.ModernUiDraw;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Replay timeline with render-frame/sub-pixel playhead motion and hover info. */
public final class Timeline extends GuiElement {

    private final DoubleUnaryOperator playheadSupplier;
    private final IntSupplier lastTickSupplier;
    private final IntConsumer seekConsumer;
    private boolean dragging;

    public Timeline(
            int x,
            int y,
            int width,
            int height,
            DoubleUnaryOperator playheadSupplier,
            IntSupplier lastTickSupplier,
            IntConsumer seekConsumer
    ) {
        super(x, y, width, height);
        this.playheadSupplier = playheadSupplier;
        this.lastTickSupplier = lastTickSupplier;
        this.seekConsumer = seekConsumer;
    }

    @Override
    protected void drawSelf(TrueTypeRenderer textRenderer, int mouseX, int mouseY, float partialTicks) {
        int ax = getAbsoluteX();
        int ay = getAbsoluteY();
        float centerY = ay + height * 0.5F;
        float trackH = physicalToLogical(3);

        ModernUiDraw.roundedRect(
                ax,
                centerY - trackH * 0.5F,
                ax + width,
                centerY + trackH * 0.5F,
                trackH * 0.5F,
                applyOpacity(LocalReplayTheme.LINE_STRONG)
        );

        int last = Math.max(0, lastTickSupplier.getAsInt());
        double playhead = Math.max(0.0D, Math.min(last, playheadSupplier.applyAsDouble(partialTicks)));
        double ratio = last <= 0 ? 0.0D : playhead / last;
        double px = ax + ratio * Math.max(0.0D, width - 1.0D);
        int progressRight = Math.max(ax, Math.min(ax + width, (int) Math.round(px)));
        if (progressRight > ax) {
            ModernUiDraw.roundedRect(
                    ax,
                    centerY - trackH * 0.5F,
                    progressRight,
                    centerY + trackH * 0.5F,
                    trackH * 0.5F,
                    applyOpacity(LocalReplayTheme.FOCUS)
            );
        }

        double physicalPixel = UiScaleContext.physicalPixel();
        double lineHalf = 6.0D / UiScaleContext.DESIGN_ZOOM;
        double squareHalf = 2.5D / UiScaleContext.DESIGN_ZOOM;
        drawSmoothPhysicalMarker(px, centerY - lineHalf, centerY + lineHalf, applyOpacity(LocalReplayTheme.FOCUS));
        drawRectSubpixel(
                px - squareHalf,
                centerY - squareHalf,
                px + squareHalf + physicalPixel,
                centerY + squareHalf + physicalPixel,
                applyOpacity(LocalReplayTheme.FOCUS)
        );

        if (hovered) {
            int hoverX = Math.max(ax, Math.min(ax + width - 1, mouseX));
            double hoverRatio = width <= 1 ? 0.0D : (hoverX - ax) / (double) (width - 1);
            int hoverTick = (int) Math.round(hoverRatio * last);
            String text = "Tick " + hoverTick + " / " + last + "  •  " + formatTickSeconds(hoverTick);
            int textWidth = textRenderer.getStringWidth(text, FontRole.MONO);
            int padX = logicalFromPhysical(8);
            int tooltipH = Math.max(logicalFromPhysical(25), textRenderer.getLineHeight(FontRole.MONO) + logicalFromPhysical(8));
            int tooltipW = textWidth + padX * 2;
            int left = Math.max(ax, Math.min(ax + width - tooltipW, hoverX - tooltipW / 2));
            int top = ay - tooltipH - logicalFromPhysical(5);
            if (top < 0) top = ay + height + logicalFromPhysical(5);

            ModernUiDraw.roundedBorder(
                    left,
                    top,
                    left + tooltipW,
                    top + tooltipH,
                    Math.max(2, logicalFromPhysical(6)),
                    applyOpacity(LocalReplayTheme.LINE_CONTROL),
                    applyOpacity(0xF70D1115)
            );
            textRenderer.drawCenteredString(
                    text,
                    left + tooltipW * 0.5F,
                    top + tooltipH * 0.5F,
                    applyOpacity(LocalReplayTheme.TEXT_STRONG),
                    FontRole.MONO
            );
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) return true;
        if (visible && enabled && mouseButton == 0 && contains(mouseX, mouseY)) {
            dragging = true;
            seekAt(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        boolean handled = super.mouseReleased(mouseX, mouseY, state);
        if (dragging && state == 0) {
            dragging = false;
            seekAt(mouseX);
            return true;
        }
        return handled;
    }

    public void mouseDragged(int mouseX) {
        if (dragging) seekAt(mouseX);
    }

    private void seekAt(int mouseX) {
        int last = Math.max(0, lastTickSupplier.getAsInt());
        int ax = getAbsoluteX();
        double ratio = width <= 1 ? 0.0D : (mouseX - ax) / (double) (width - 1);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        seekConsumer.accept((int) Math.round(ratio * last));
    }

    private static String formatTickSeconds(int tick) {
        return String.format(Locale.ROOT, "%.3f sec", Math.max(0, tick) / 20.0D);
    }

    private static int logicalFromPhysical(int pixels) {
        return UiScaleContext.fromPhysical(pixels);
    }

    private static float physicalToLogical(int pixels) {
        return pixels / UiScaleContext.DESIGN_ZOOM;
    }

    private static void drawSmoothPhysicalMarker(double logicalX, double top, double bottom, int color) {
        double scale = UiScaleContext.DESIGN_ZOOM;
        double physicalX = logicalX * scale;
        int leftPixel = (int) Math.floor(physicalX);
        double fraction = physicalX - leftPixel;
        double pixelWidth = 1.0D / scale;

        int baseAlpha = (color >>> 24) & 0xFF;
        int leftAlpha = (int) Math.round(baseAlpha * (1.0D - fraction));
        int rightAlpha = (int) Math.round(baseAlpha * fraction);
        int rgb = color & 0x00FFFFFF;

        if (leftAlpha > 0) {
            double x0 = leftPixel / (double) scale;
            drawRectSubpixel(x0, top, x0 + pixelWidth, bottom, (leftAlpha << 24) | rgb);
        }
        if (rightAlpha > 0) {
            double x0 = (leftPixel + 1) / (double) scale;
            drawRectSubpixel(x0, top, x0 + pixelWidth, bottom, (rightAlpha << 24) | rgb);
        }
    }

    private static void drawRectSubpixel(double left, double top, double right, double bottom, int color) {
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, bottom, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(right, bottom, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(right, top, 0.0D).color(r, g, b, a).endVertex();
        buffer.pos(left, top, 0.0D).color(r, g, b, a).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
