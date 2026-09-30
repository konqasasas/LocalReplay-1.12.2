package com.konqasasas.localreplay.gui.font;

/**
 * One rasterized glyph inside a high-resolution atlas.
 * Atlas coordinates are physical texture pixels; drawing metrics are logical
 * Minecraft GUI units.
 */
final class Glyph {
    final int codePoint;
    final float advance;
    final float drawWidth;
    final float drawHeight;
    final float padding;
    final int rawDrawWidth;
    final int rawDrawHeight;
    final int atlasX;
    final int atlasY;

    /** Visible-alpha bounds relative to the drawString origin, in GUI units. */
    final float inkMinX;
    final float inkMinY;
    final float inkMaxX;
    final float inkMaxY;
    final boolean hasInk;

    Glyph(
            int codePoint,
            float advance,
            float drawWidth,
            float drawHeight,
            float padding,
            int rawDrawWidth,
            int rawDrawHeight,
            int atlasX,
            int atlasY,
            float inkMinX,
            float inkMinY,
            float inkMaxX,
            float inkMaxY,
            boolean hasInk
    ) {
        this.codePoint = codePoint;
        this.advance = advance;
        this.drawWidth = drawWidth;
        this.drawHeight = drawHeight;
        this.padding = padding;
        this.rawDrawWidth = rawDrawWidth;
        this.rawDrawHeight = rawDrawHeight;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.inkMinX = inkMinX;
        this.inkMinY = inkMinY;
        this.inkMaxX = inkMaxX;
        this.inkMaxY = inkMaxY;
        this.hasInk = hasInk;
    }
}
