package com.konqasasas.localreplay.gui.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;

/**
 * Anti-aliased UI primitives for the 1.12.2 GUI renderer.
 *
 * Rounded corners use a reusable supersampled alpha mask instead of scanline
 * approximation. Borders are inset by exactly one physical framebuffer pixel,
 * so GUI Scale 2/3/4 does not turn them into thick or uneven outlines.
 */
public final class ModernUiDraw {

    private static final int MASK_SIZE = 128;
    private static final int SAMPLES = 8;
    private static ResourceLocation cornerMask;

    private ModernUiDraw() {
    }

    public static void roundedRect(float left, float top, float right, float bottom, float radius, int color) {
        if (right <= left || bottom <= top) return;

        left = pixelAligned(left);
        top = pixelAligned(top);
        right = pixelAligned(right);
        bottom = pixelAligned(bottom);

        float maxRadius = Math.min(right - left, bottom - top) / 2.0F;
        radius = Math.max(0.0F, Math.min(pixelAlignedLength(radius), maxRadius));

        if (radius < physicalPixel()) {
            solidRect(left, top, right, bottom, color);
            return;
        }

        // Rectangular spans cover the body; only the curved coverage comes from
        // the filtered corner mask. This avoids internal seams.
        solidRect(left + radius, top, right - radius, bottom, color);
        solidRect(left, top + radius, right, bottom - radius, color);
        drawCorners(left, top, right, bottom, radius, color);
    }

    public static void roundedBorder(
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int borderColor,
            int fillColor
    ) {
        roundedRect(left, top, right, bottom, radius, borderColor);

        float inset = physicalPixel();
        if (right - left > inset * 2.0F && bottom - top > inset * 2.0F) {
            roundedRect(
                    left + inset,
                    top + inset,
                    right - inset,
                    bottom - inset,
                    Math.max(0.0F, radius - inset),
                    fillColor
            );
        }
    }

    /** Exactly one physical framebuffer pixel, regardless of Minecraft GUI Scale. */
    public static void horizontalLine(float left, float right, float y, int color) {
        float pixel = physicalPixel();
        y = pixelAligned(y);
        solidRect(pixelAligned(left), y, pixelAligned(right), y + pixel, color);
    }

    /** Exactly one physical framebuffer pixel, regardless of Minecraft GUI Scale. */
    public static void verticalLine(float x, float top, float bottom, int color) {
        float pixel = physicalPixel();
        x = pixelAligned(x);
        solidRect(x, pixelAligned(top), x + pixel, pixelAligned(bottom), color);
    }

    private static void drawCorners(
            float left,
            float top,
            float right,
            float bottom,
            float radius,
            int color
    ) {
        ensureMask();

        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();

        // GUI rendering normally keeps alpha testing enabled. That cutoff would
        // discard the faint edge samples responsible for anti-aliasing.
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);

        Minecraft.getMinecraft().getTextureManager().bindTexture(cornerMask);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        corner(buffer, left, top, left + radius, top + radius, 0, 0, 1, 1);
        corner(buffer, right - radius, top, right, top + radius, 1, 0, 0, 1);
        corner(buffer, left, bottom - radius, left + radius, bottom, 0, 1, 1, 0);
        corner(buffer, right - radius, bottom - radius, right, bottom, 1, 1, 0, 0);

        tessellator.draw();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    private static void corner(
            BufferBuilder buffer,
            float left,
            float top,
            float right,
            float bottom,
            float u0,
            float v0,
            float u1,
            float v1
    ) {
        buffer.pos(left, bottom, 0.0D).tex(u0, v1).endVertex();
        buffer.pos(right, bottom, 0.0D).tex(u1, v1).endVertex();
        buffer.pos(right, top, 0.0D).tex(u1, v0).endVertex();
        buffer.pos(left, top, 0.0D).tex(u0, v0).endVertex();
    }

    private static void solidRect(float left, float top, float right, float bottom, int color) {
        if (right <= left || bottom <= top) return;

        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buffer.pos(left, bottom, 0.0D).endVertex();
        buffer.pos(right, bottom, 0.0D).endVertex();
        buffer.pos(right, top, 0.0D).endVertex();
        buffer.pos(left, top, 0.0D).endVertex();
        tessellator.draw();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void ensureMask() {
        if (cornerMask != null) return;

        BufferedImage image = new BufferedImage(MASK_SIZE, MASK_SIZE, BufferedImage.TYPE_INT_ARGB);
        int sampleCount = SAMPLES * SAMPLES;

        for (int y = 0; y < MASK_SIZE; y++) {
            for (int x = 0; x < MASK_SIZE; x++) {
                int covered = 0;

                for (int sampleY = 0; sampleY < SAMPLES; sampleY++) {
                    for (int sampleX = 0; sampleX < SAMPLES; sampleX++) {
                        double px = (x + (sampleX + 0.5D) / SAMPLES) / MASK_SIZE;
                        double py = (y + (sampleY + 0.5D) / SAMPLES) / MASK_SIZE;
                        double dx = 1.0D - px;
                        double dy = 1.0D - py;

                        if (dx * dx + dy * dy <= 1.0D) {
                            covered++;
                        }
                    }
                }

                int alpha = Math.round(255.0F * covered / sampleCount);
                image.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        cornerMask = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation("localreplay-rounded-corner", texture);

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(previousTexture);
    }

    private static float physicalPixel() {
        return UiScaleContext.physicalPixel();
    }

    private static float pixelAligned(float value) {
        return UiScaleContext.snap(value);
    }

    private static float pixelAlignedLength(float value) {
        return UiScaleContext.snapLength(value);
    }
}
