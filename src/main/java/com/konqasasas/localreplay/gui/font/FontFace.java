package com.konqasasas.localreplay.gui.font;

import net.minecraft.client.Minecraft;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** A font rasterized above screen resolution and downsampled by OpenGL. */
final class FontFace {

    private final Minecraft minecraft;
    private final String internalId;
    private final Font font;
    private final float rasterScale;
    private final FontMetrics metrics;
    private final FontRenderContext renderContext;
    private final Map<Integer, GlyphAtlas> pages = new HashMap<>();

    FontFace(Minecraft minecraft, String internalId, Font font, float rasterScale) {
        this.minecraft = minecraft;
        this.internalId = internalId;
        this.font = font;
        this.rasterScale = Math.max(1.0F, rasterScale);

        BufferedImage metricsImage = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = metricsImage.createGraphics();
        try {
            configureGraphics(g);
            g.setFont(font);
            this.metrics = g.getFontMetrics(font);
            this.renderContext = g.getFontRenderContext();
        } finally {
            g.dispose();
        }
    }

    void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    String getInternalId() {
        return internalId;
    }

    Font getFont() {
        return font;
    }

    FontMetrics getMetrics() {
        return metrics;
    }

    float getRasterScale() {
        return rasterScale;
    }

    boolean canDisplay(int codePoint) {
        return font.canDisplay(codePoint);
    }

    GlyphAtlas getAtlasFor(int codePoint) {
        int page = codePoint >> 8;
        GlyphAtlas atlas = pages.get(page);
        if (atlas == null) {
            atlas = new GlyphAtlas(minecraft, this, page);
            pages.put(page, atlas);
        }
        return atlas;
    }

    float getAdvanceRaw(int codePoint) {
        if (!canDisplay(codePoint)) {
            return 0.0F;
        }
        String value = new String(Character.toChars(codePoint));
        Rectangle2D bounds = font.getStringBounds(value, renderContext);
        return (float) bounds.getWidth();
    }

    float getAdvanceLogical(int codePoint) {
        return getAdvanceRaw(codePoint) / rasterScale;
    }

    float getLineHeightLogical() {
        return metrics.getHeight() / rasterScale;
    }

    void close() {
        for (GlyphAtlas atlas : pages.values()) {
            atlas.delete();
        }
        pages.clear();
    }
}
