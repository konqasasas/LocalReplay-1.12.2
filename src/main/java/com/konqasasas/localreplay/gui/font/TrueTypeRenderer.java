package com.konqasasas.localreplay.gui.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import com.konqasasas.localreplay.gui.theme.UiScaleContext;

/** High-resolution glyph-atlas renderer for Local Replay UI text. */
public final class TrueTypeRenderer {

    public static final class VisualBounds {
        public final float minX;
        public final float minY;
        public final float maxX;
        public final float maxY;
        public final boolean empty;

        VisualBounds(float minX, float minY, float maxX, float maxY, boolean empty) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.empty = empty;
        }

        public float getWidth() {
            return empty ? 0.0F : maxX - minX;
        }

        public float getHeight() {
            return empty ? 0.0F : maxY - minY;
        }
    }

    private final Minecraft minecraft;
    private final FontManager fonts;

    public TrueTypeRenderer(Minecraft minecraft, FontManager fonts) {
        this.minecraft = minecraft;
        this.fonts = fonts;
    }

    public int drawString(String text, float x, float y, int argb, FontRole role) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        x = UiScaleContext.snap(x);
        y = UiScaleContext.snap(y);
        float startX = x;
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);

        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (codePoint == '\n') {
                x = startX;
                y += getLineHeight(role);
                continue;
            }

            FontFace face = fonts.chooseFace(codePoint, role);
            GlyphAtlas atlas = face.getAtlasFor(codePoint);
            Glyph glyph = atlas.getGlyph(codePoint);
            if (glyph == null) {
                continue;
            }

            minecraft.getTextureManager().bindTexture(atlas.getTextureLocation());

            float drawX = x - glyph.padding;
            float drawY = y - glyph.padding;
            float w = glyph.drawWidth;
            float h = glyph.drawHeight;

            float u0 = glyph.atlasX / (float) atlas.getAtlasWidth();
            float v0 = glyph.atlasY / (float) atlas.getAtlasHeight();
            float u1 = (glyph.atlasX + glyph.rawDrawWidth) / (float) atlas.getAtlasWidth();
            float v1 = (glyph.atlasY + glyph.rawDrawHeight) / (float) atlas.getAtlasHeight();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(drawX, drawY + h, 0.0D).tex(u0, v1).endVertex();
            buffer.pos(drawX + w, drawY + h, 0.0D).tex(u1, v1).endVertex();
            buffer.pos(drawX + w, drawY, 0.0D).tex(u1, v0).endVertex();
            buffer.pos(drawX, drawY, 0.0D).tex(u0, v0).endVertex();
            tessellator.draw();

            x += glyph.advance;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        return Math.round(x - startX);
    }

    /**
     * Draw text so its actual visible pixels, not merely its advance/line box,
     * are centered on the requested point. This keeps symbols such as ◀/▶ and
     * normal Latin labels visually centered in the same control geometry.
     */
    public int drawCenteredString(String text, float centerX, float centerY, int argb, FontRole role) {
        VisualBounds bounds = getVisualBounds(text, role);
        if (bounds.empty) {
            return drawString(text, centerX - getStringWidth(text, role) / 2.0F,
                    centerY - getLineHeight(role) / 2.0F, argb, role);
        }
        float x = centerX - (bounds.minX + bounds.maxX) * 0.5F;
        float y = centerY - (bounds.minY + bounds.maxY) * 0.5F;
        return drawString(text, x, y, argb, role);
    }

    public VisualBounds getVisualBounds(String text, FontRole role) {
        if (text == null || text.isEmpty()) {
            return new VisualBounds(0, 0, 0, 0, true);
        }

        float penX = 0.0F;
        float penY = 0.0F;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean hasInk = false;

        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                penX = 0.0F;
                penY += getLineHeight(role);
                continue;
            }

            FontFace face = fonts.chooseFace(codePoint, role);
            GlyphAtlas atlas = face.getAtlasFor(codePoint);
            Glyph glyph = atlas.getGlyph(codePoint);
            if (glyph == null) {
                continue;
            }

            if (glyph.hasInk) {
                hasInk = true;
                minX = Math.min(minX, penX + glyph.inkMinX);
                minY = Math.min(minY, penY + glyph.inkMinY);
                maxX = Math.max(maxX, penX + glyph.inkMaxX);
                maxY = Math.max(maxY, penY + glyph.inkMaxY);
            }
            penX += glyph.advance;
        }

        if (!hasInk) {
            return new VisualBounds(0, 0, 0, 0, true);
        }
        return new VisualBounds(minX, minY, maxX, maxY, false);
    }

    public int getStringWidth(String text, FontRole role) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        float current = 0.0F;
        float max = 0.0F;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n') {
                max = Math.max(max, current);
                current = 0.0F;
                continue;
            }
            current += fonts.getStringAdvance(codePoint, role);
        }
        return Math.max(0, Math.round(Math.max(max, current)));
    }

    public int getLineHeight(FontRole role) {
        return fonts.getLineHeight(role);
    }
}
