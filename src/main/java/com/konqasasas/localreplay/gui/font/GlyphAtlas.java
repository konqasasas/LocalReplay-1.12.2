package com.konqasasas.localreplay.gui.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * One 256-codepoint page of a supersampled TrueType atlas.
 * The texture stays high resolution; glyph quads are smaller logical GUI units,
 * so OpenGL performs a quality downsample rather than magnifying tiny glyphs.
 */
final class GlyphAtlas {

    private static final int GRID = 16;
    private static final float LOGICAL_PADDING = 2.0F;

    private final int atlasWidth;
    private final int atlasHeight;
    private final int lineHeight;
    private final ResourceLocation textureLocation;
    private final DynamicTexture texture;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    GlyphAtlas(Minecraft minecraft, FontFace face, int pageIndex) {
        int firstCodePoint = pageIndex << 8;
        float rasterScale = face.getRasterScale();
        int padding = Math.max(2, Math.round(LOGICAL_PADDING * rasterScale));

        FontMetrics metrics = face.getMetrics();
        int maxAdvance = 1;
        for (int i = 0; i < 256; i++) {
            int cp = firstCodePoint + i;
            if (Character.isValidCodePoint(cp) && face.canDisplay(cp)) {
                maxAdvance = Math.max(maxAdvance, (int) Math.ceil(face.getAdvanceRaw(cp)));
            }
        }

        int cellWidth = Math.max(8, maxAdvance + padding * 2 + 2);
        int cellHeight = Math.max(8, metrics.getHeight() + padding * 2 + 2);
        this.lineHeight = Math.max(1, Math.round(metrics.getHeight() / rasterScale));
        this.atlasWidth = nextPowerOfTwo(cellWidth * GRID);
        this.atlasHeight = nextPowerOfTwo(cellHeight * GRID);

        BufferedImage image = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(255, 255, 255, 0));
            g.fillRect(0, 0, atlasWidth, atlasHeight);
            g.setComposite(AlphaComposite.SrcOver);
            face.configureGraphics(g);
            g.setFont(face.getFont());
            g.setColor(Color.WHITE);

            for (int i = 0; i < 256; i++) {
                int cp = firstCodePoint + i;
                if (!Character.isValidCodePoint(cp) || !face.canDisplay(cp)) {
                    continue;
                }

                String text = new String(Character.toChars(cp));
                float advanceRaw = Math.max(0.0F, face.getAdvanceRaw(cp));
                int rawDrawWidth = Math.min(cellWidth, Math.max(1, (int) Math.ceil(advanceRaw) + padding * 2 + 2));
                int rawDrawHeight = cellHeight;
                int col = i & 15;
                int row = i >> 4;
                int x = col * cellWidth;
                int y = row * cellHeight;

                g.drawString(text, x + padding, y + padding + metrics.getAscent());

                int minAlphaX = rawDrawWidth;
                int minAlphaY = rawDrawHeight;
                int maxAlphaX = -1;
                int maxAlphaY = -1;
                for (int py = 0; py < rawDrawHeight; py++) {
                    for (int px = 0; px < rawDrawWidth; px++) {
                        int alpha = (image.getRGB(x + px, y + py) >>> 24) & 0xFF;
                        if (alpha == 0) {
                            continue;
                        }
                        minAlphaX = Math.min(minAlphaX, px);
                        minAlphaY = Math.min(minAlphaY, py);
                        maxAlphaX = Math.max(maxAlphaX, px);
                        maxAlphaY = Math.max(maxAlphaY, py);
                    }
                }

                float logicalPadding = padding / rasterScale;
                boolean hasInk = maxAlphaX >= minAlphaX && maxAlphaY >= minAlphaY;
                float inkMinX = 0.0F;
                float inkMinY = 0.0F;
                float inkMaxX = 0.0F;
                float inkMaxY = 0.0F;
                if (hasInk) {
                    inkMinX = -logicalPadding + minAlphaX / rasterScale;
                    inkMinY = -logicalPadding + minAlphaY / rasterScale;
                    inkMaxX = -logicalPadding + (maxAlphaX + 1) / rasterScale;
                    inkMaxY = -logicalPadding + (maxAlphaY + 1) / rasterScale;
                }

                glyphs.put(cp, new Glyph(
                        cp,
                        advanceRaw / rasterScale,
                        rawDrawWidth / rasterScale,
                        rawDrawHeight / rasterScale,
                        logicalPadding,
                        rawDrawWidth,
                        rawDrawHeight,
                        x,
                        y,
                        inkMinX,
                        inkMinY,
                        inkMaxX,
                        inkMaxY,
                        hasInk
                ));
            }
        } finally {
            g.dispose();
        }

        this.texture = new DynamicTexture(image);
        String key = "localreplay_font_" + face.getInternalId() + "_" + pageIndex;
        this.textureLocation = minecraft.getTextureManager().getDynamicTextureLocation(key, texture);

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        minecraft.getTextureManager().bindTexture(textureLocation);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.bindTexture(previousTexture);
    }

    Glyph getGlyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    int getAtlasWidth() {
        return atlasWidth;
    }

    int getAtlasHeight() {
        return atlasHeight;
    }

    int getLineHeight() {
        return lineHeight;
    }

    ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    void delete() {
        texture.deleteGlTexture();
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }
}
