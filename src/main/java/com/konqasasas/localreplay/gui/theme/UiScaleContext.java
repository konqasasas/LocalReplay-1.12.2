package com.konqasasas.localreplay.gui.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * One stable virtual coordinate system for Local Replay UI.
 *
 * Minecraft's GuiScreen coordinates change density with GUI Scale. Local Replay
 * instead lays out UI in design pixels and applies one matrix transform at the
 * screen boundary, matching the AST approach. A design unit therefore maps to
 * the same physical density regardless of Minecraft GUI Scale.
 */
public final class UiScaleContext {

    public static final float DESIGN_ZOOM = 1.25F;

    private final float designToMinecraftScale;
    private final float minecraftToDesignScale;
    private final int virtualWidth;
    private final int virtualHeight;

    private UiScaleContext(float designToMinecraftScale, int virtualWidth, int virtualHeight) {
        this.designToMinecraftScale = designToMinecraftScale;
        this.minecraftToDesignScale = 1.0F / designToMinecraftScale;
        this.virtualWidth = Math.max(1, virtualWidth);
        this.virtualHeight = Math.max(1, virtualHeight);
    }

    public static UiScaleContext forScreen(Minecraft minecraft, int guiWidth, int guiHeight) {
        int minecraftScale = 1;
        try {
            minecraftScale = Math.max(1, new ScaledResolution(minecraft).getScaleFactor());
        } catch (Throwable ignored) {
        }
        float toMinecraft = DESIGN_ZOOM / minecraftScale;
        return new UiScaleContext(
                toMinecraft,
                Math.round(guiWidth / toMinecraft),
                Math.round(guiHeight / toMinecraft)
        );
    }

    public int getVirtualWidth() {
        return virtualWidth;
    }

    public int getVirtualHeight() {
        return virtualHeight;
    }

    public int toDesignX(int minecraftGuiX) {
        return Math.round(minecraftGuiX * minecraftToDesignScale);
    }

    public int toDesignY(int minecraftGuiY) {
        return Math.round(minecraftGuiY * minecraftToDesignScale);
    }

    public void begin() {
        GlStateManager.pushMatrix();
        GlStateManager.scale(designToMinecraftScale, designToMinecraftScale, 1.0F);
    }

    public void end() {
        GlStateManager.popMatrix();
    }

    /** Convert a desired physical-pixel size into stable design units. */
    public static int fromPhysical(int physicalPixels) {
        if (physicalPixels <= 0) return 0;
        return Math.max(1, Math.round(physicalPixels / DESIGN_ZOOM));
    }

    /** Exactly one physical framebuffer pixel in the virtual design space. */
    public static float physicalPixel() {
        return 1.0F / DESIGN_ZOOM;
    }

    public static float snap(float value) {
        return Math.round(value * DESIGN_ZOOM) / DESIGN_ZOOM;
    }

    public static float snapLength(float value) {
        return Math.max(0.0F, Math.round(value * DESIGN_ZOOM) / DESIGN_ZOOM);
    }
}
