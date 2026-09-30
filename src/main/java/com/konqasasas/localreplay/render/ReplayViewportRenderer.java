package com.konqasasas.localreplay.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Captures the completed world image immediately before Forge starts drawing
 * the in-game HUD, then presents it in the Local Replay viewer.
 *
 * The previous Catmull-Rom presentation shader was intentionally removed. Its
 * negative reconstruction lobes could create a large dark halo around the
 * high-contrast horizon. Minecraft's full-resolution frame is already larger
 * than an inset viewport, so ordinary linear minification is clean and stable.
 * Full-screen mode uses exact GUI bounds with no aspect-fit transform.
 */
public final class ReplayViewportRenderer {

    private static final int GL_CLAMP_TO_EDGE = 0x812F;

    private final Minecraft minecraft;
    private int textureId = -1;
    private int textureWidth;
    private int textureHeight;
    private boolean hasCapturedFrame;

    public ReplayViewportRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /** Capture the main framebuffer after world rendering, before HUD drawing. */
    public void captureWorldFrame() {
        if (minecraft.getFramebuffer() == null) {
            return;
        }

        int width = Math.max(1, minecraft.getFramebuffer().framebufferWidth);
        int height = Math.max(1, minecraft.getFramebuffer().framebufferHeight);
        ensureTexture(width, height);

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(textureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        GlStateManager.bindTexture(previousTexture);
        hasCapturedFrame = true;
    }

    public void drawFitted(int x, int y, int width, int height) {
        if (!hasCapturedFrame || textureId < 0 || textureWidth <= 0 || textureHeight <= 0
                || width <= 0 || height <= 0) {
            return;
        }

        FittedBounds fitted = getFittedBounds(x, y, width, height);
        if (fitted == null) {
            return;
        }
        drawTexture(fitted.getX(), fitted.getY(), fitted.getWidth(), fitted.getHeight());
    }

    /** Draw the captured frame into the exact GUI-space rectangle. */
    public void drawExact(int x, int y, int width, int height) {
        if (!hasCapturedFrame || textureId < 0 || textureWidth <= 0 || textureHeight <= 0
                || width <= 0 || height <= 0) {
            return;
        }
        drawTexture(x, y, width, height);
    }

    private void drawTexture(int drawX, int drawY, int drawWidth, int drawHeight) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.bindTexture(textureId);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(drawX, drawY + drawHeight, 0.0D).tex(0.0D, 0.0D).endVertex();
            buffer.pos(drawX + drawWidth, drawY + drawHeight, 0.0D).tex(1.0D, 0.0D).endVertex();
            buffer.pos(drawX + drawWidth, drawY, 0.0D).tex(1.0D, 1.0D).endVertex();
            buffer.pos(drawX, drawY, 0.0D).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.bindTexture(previousTexture);
            GL11.glPopAttrib();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }


    /**
     * Returns the exact GUI-space rectangle used by {@link #drawFitted}.
     * This lets replay-only overlays (notably vanilla F3) line up with the
     * actual world image instead of the viewport's letterbox area.
     */
    public FittedBounds getFittedBounds(int x, int y, int width, int height) {
        if (textureWidth <= 0 || textureHeight <= 0 || width <= 0 || height <= 0) {
            return null;
        }

        double sourceAspect = textureWidth / (double) textureHeight;
        double targetAspect = width / (double) height;

        int drawWidth = width;
        int drawHeight = height;
        if (targetAspect > sourceAspect) {
            drawWidth = Math.max(1, (int) Math.round(height * sourceAspect));
        } else {
            drawHeight = Math.max(1, (int) Math.round(width / sourceAspect));
        }

        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        return new FittedBounds(drawX, drawY, drawWidth, drawHeight);
    }

    public static final class FittedBounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private FittedBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
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
    }

    public void close() {
        hasCapturedFrame = false;
        if (textureId >= 0) {
            GL11.glDeleteTextures(textureId);
            textureId = -1;
            textureWidth = 0;
            textureHeight = 0;
        }
    }

    private void ensureTexture(int width, int height) {
        if (textureId >= 0 && textureWidth == width && textureHeight == height) {
            return;
        }

        if (textureId >= 0) {
            GL11.glDeleteTextures(textureId);
        }

        textureId = GL11.glGenTextures();
        textureWidth = width;
        textureHeight = height;
        hasCapturedFrame = false;

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null
        );
        GlStateManager.bindTexture(previousTexture);
    }
}
