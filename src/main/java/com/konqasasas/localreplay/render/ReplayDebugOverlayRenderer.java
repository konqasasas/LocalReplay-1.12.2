package com.konqasasas.localreplay.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Re-composes only vanilla's F3 debug overlay on top of the replay viewport.
 *
 * The in-game HUD is cancelled before ReplayScreen captures the world frame, so
 * hotbar/health/crosshair never become part of that texture. When F3 is enabled,
 * this renderer invokes the vanilla GuiOverlayDebug afterwards and maps the
 * normal full-screen debug coordinate space into the fitted replay image.
 *
 * GuiOverlayDebug reads XYZ/facing from Minecraft#getRenderViewEntity(). During
 * replay that entity is Local Replay's client-only camera, so the position and
 * rotation lines follow the replay without touching the real player.
 */
public final class ReplayDebugOverlayRenderer {

    private final Minecraft minecraft;
    private final GuiOverlayDebug overlayDebug;

    public ReplayDebugOverlayRenderer(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft cannot be null");
        }
        this.minecraft = minecraft;
        this.overlayDebug = new GuiOverlayDebug(minecraft);
    }

    /** Render vanilla F3 in the normal full-screen GUI coordinate space. */
    public void renderFullScreen() {
        if (!minecraft.gameSettings.showDebugInfo) {
            return;
        }

        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int[] previousScissor = readScissorBox();

        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, minecraft.displayWidth, minecraft.displayHeight);
            overlayDebug.renderDebugInfo(scaledResolution);
        } finally {
            restoreScissor(scissorWasEnabled, previousScissor);
            if (depthWasEnabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void render(ReplayViewportRenderer.FittedBounds bounds) {
        if (bounds == null || !minecraft.gameSettings.showDebugInfo) {
            return;
        }
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return;
        }

        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int sourceWidth = Math.max(1, scaledResolution.getScaledWidth());
        int sourceHeight = Math.max(1, scaledResolution.getScaledHeight());

        // Preserve aspect ratio instead of stretching vanilla's font. The
        // viewport frame uses the display aspect ratio too, so these two ratios
        // normally differ only by ScaledResolution rounding.
        double scale = Math.min(
                bounds.getWidth() / (double) sourceWidth,
                bounds.getHeight() / (double) sourceHeight
        );
        if (!(scale > 0.0D) || Double.isInfinite(scale) || Double.isNaN(scale)) {
            return;
        }

        double renderedWidth = sourceWidth * scale;
        double renderedHeight = sourceHeight * scale;
        double originX = bounds.getX() + (bounds.getWidth() - renderedWidth) * 0.5D;
        double originY = bounds.getY() + (bounds.getHeight() - renderedHeight) * 0.5D;

        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int[] previousScissor = readScissorBox();

        GlStateManager.pushMatrix();
        try {
            enableScissor(bounds);
            GlStateManager.translate(originX, originY, 0.0D);
            GlStateManager.scale(scale, scale, 1.0D);
            overlayDebug.renderDebugInfo(scaledResolution);
        } finally {
            GlStateManager.popMatrix();
            restoreScissor(scissorWasEnabled, previousScissor);
            if (depthWasEnabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int[] readScissorBox() {
        // LWJGL 2 validates glGetInteger(IntBuffer) against the maximum number of
        // values that the binding may return, not only GL_SCISSOR_BOX's four
        // values. A 4-int buffer therefore throws BufferChecks on some setups.
        java.nio.IntBuffer buffer = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, buffer);
        return new int[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    private static void restoreScissor(boolean wasEnabled, int[] box) {
        if (box != null && box.length >= 4) {
            GL11.glScissor(box[0], box[1], box[2], box[3]);
        }
        if (wasEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void enableScissor(ReplayViewportRenderer.FittedBounds bounds) {
        ScaledResolution scaledResolution = new ScaledResolution(minecraft);
        int screenWidth = Math.max(1, scaledResolution.getScaledWidth());
        int screenHeight = Math.max(1, scaledResolution.getScaledHeight());

        double pxPerGuiX = minecraft.displayWidth / (double) screenWidth;
        double pxPerGuiY = minecraft.displayHeight / (double) screenHeight;

        int left = clamp(
                (int) Math.floor(bounds.getX() * pxPerGuiX),
                0,
                minecraft.displayWidth
        );
        int right = clamp(
                (int) Math.ceil((bounds.getX() + bounds.getWidth()) * pxPerGuiX),
                0,
                minecraft.displayWidth
        );
        int top = clamp(
                (int) Math.floor(bounds.getY() * pxPerGuiY),
                0,
                minecraft.displayHeight
        );
        int bottom = clamp(
                (int) Math.ceil((bounds.getY() + bounds.getHeight()) * pxPerGuiY),
                0,
                minecraft.displayHeight
        );

        int scissorX = left;
        int scissorY = minecraft.displayHeight - bottom;
        int scissorWidth = Math.max(0, right - left);
        int scissorHeight = Math.max(0, bottom - top);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
