package com.konqasasas.localreplay.gui.font;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Local Replay font stack.
 *
 * Glyphs are rasterized in Local Replay design units at 2x supersampling.
 * GuiScale is deliberately not captured here; UiScaleContext applies the one
 * screen-level transform so text metrics and component geometry share a single
 * coordinate system.
 */
public final class FontManager {

    public static final int DEFAULT_PHYSICAL_PIXEL_SIZE = 12;
    private static final int SUPERSAMPLE = 2;

    private final FontFace uiFace;
    private final FontFace semiboldFace;
    private final FontFace smallUiFace;
    private final FontFace japaneseFace;
    private final FontFace japaneseBoldFace;
    private final FontFace smallJapaneseFace;
    private final FontFace monoFace;
    private final FontFace monoLargeFace;
    private final Logger logger;
    private final float rasterScale;

    public FontManager(Minecraft minecraft, Logger logger) {
        this.logger = logger;
        Set<String> installed = installedFamilies();

        // Font metrics live in Local Replay design units, not Minecraft GUI units.
        // The screen-level UiScaleContext handles GUI Scale once for the whole UI.
        this.rasterScale = (float) SUPERSAMPLE;
        int logicalFontSize = DEFAULT_PHYSICAL_PIXEL_SIZE;
        int smallLogicalFontSize = 10;
        int largeMonoLogicalFontSize = 13;
        int rasterFontSize = Math.max(8, Math.round(logicalFontSize * rasterScale));
        int smallRasterFontSize = Math.max(8, Math.round(smallLogicalFontSize * rasterScale));
        int largeMonoRasterFontSize = Math.max(8, Math.round(largeMonoLogicalFontSize * rasterScale));

        Font ui = resolve(installed, rasterFontSize,
                "IBM Plex Sans", "Arial", "SansSerif");
        Font semibold = ui.deriveFont(Font.BOLD, rasterFontSize);
        Font smallUi = ui.deriveFont(Font.PLAIN, smallRasterFontSize);
        Font japanese = resolve(installed, rasterFontSize,
                "BIZ UDPGothic", "Yu Gothic UI", "Yu Gothic", "Meiryo", "Dialog");
        Font japaneseBold = japanese.deriveFont(Font.BOLD, rasterFontSize);
        Font smallJapanese = japanese.deriveFont(Font.PLAIN, smallRasterFontSize);
        Font mono = resolve(installed, rasterFontSize,
                "IBM Plex Mono", "Consolas", "Monospaced");
        Font monoLarge = mono.deriveFont(Font.PLAIN, largeMonoRasterFontSize);

        this.uiFace = new FontFace(minecraft, "ui", ui, rasterScale);
        this.semiboldFace = new FontFace(minecraft, "ui-semibold", semibold, rasterScale);
        this.smallUiFace = new FontFace(minecraft, "ui-small", smallUi, rasterScale);
        this.japaneseFace = new FontFace(minecraft, "jp", japanese, rasterScale);
        this.japaneseBoldFace = new FontFace(minecraft, "jp-bold", japaneseBold, rasterScale);
        this.smallJapaneseFace = new FontFace(minecraft, "jp-small", smallJapanese, rasterScale);
        this.monoFace = new FontFace(minecraft, "mono", mono, rasterScale);
        this.monoLargeFace = new FontFace(minecraft, "mono-large", monoLarge, rasterScale);

        if (logger != null) {
            logger.info(
                    "Local Replay fonts: UI='{}', Japanese='{}', Mono='{}', designSize={}px, supersample={}x",
                    ui.getFamily(), japanese.getFamily(), mono.getFamily(), logicalFontSize, SUPERSAMPLE
            );
        }
    }

    FontFace chooseFace(int codePoint, FontRole role) {
        if (codePoint > 0x7F) {
            FontFace jp = role == FontRole.UI_SEMIBOLD ? japaneseBoldFace
                    : role == FontRole.UI_SMALL ? smallJapaneseFace : japaneseFace;
            if (jp.canDisplay(codePoint)) return jp;
        }

        FontFace preferred;
        if (role == FontRole.MONO_LARGE) preferred = monoLargeFace;
        else if (role == FontRole.MONO) preferred = monoFace;
        else if (role == FontRole.UI_SEMIBOLD) preferred = semiboldFace;
        else if (role == FontRole.UI_SMALL) preferred = smallUiFace;
        else preferred = uiFace;
        if (preferred.canDisplay(codePoint)) return preferred;

        FontFace fallbackJapanese = role == FontRole.UI_SEMIBOLD ? japaneseBoldFace
                : role == FontRole.UI_SMALL ? smallJapaneseFace : japaneseFace;
        if (fallbackJapanese.canDisplay(codePoint)) return fallbackJapanese;
        if (uiFace.canDisplay(codePoint)) return uiFace;
        return preferred;
    }

    int getLineHeight(FontRole role) {
        FontFace face;
        if (role == FontRole.MONO_LARGE) face = monoLargeFace;
        else if (role == FontRole.MONO) face = monoFace;
        else if (role == FontRole.UI_SEMIBOLD) face = semiboldFace;
        else if (role == FontRole.UI_SMALL) face = smallUiFace;
        else face = uiFace;
        return Math.max(1, Math.round(face.getLineHeightLogical()));
    }

    float getStringAdvance(int codePoint, FontRole role) {
        return chooseFace(codePoint, role).getAdvanceLogical(codePoint);
    }


    public String getUiFontName() {
        return uiFace.getFont().getFamily();
    }

    public String getJapaneseFontName() {
        return japaneseFace.getFont().getFamily();
    }

    public String getMonoFontName() {
        return monoFace.getFont().getFamily();
    }

    public void close() {
        uiFace.close();
        semiboldFace.close();
        smallUiFace.close();
        japaneseFace.close();
        japaneseBoldFace.close();
        smallJapaneseFace.close();
        monoFace.close();
        monoLargeFace.close();
    }


    private static Font resolve(Set<String> installed, int size, String... candidates) {
        for (String candidate : candidates) {
            if (isLogicalFont(candidate) || installed.contains(candidate.toLowerCase(Locale.ROOT))) {
                return new Font(candidate, Font.PLAIN, size);
            }
        }
        return new Font("SansSerif", Font.PLAIN, size);
    }

    private static Set<String> installedFamilies() {
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            Set<String> result = new HashSet<>();
            Arrays.stream(names).forEach(name -> result.add(name.toLowerCase(Locale.ROOT)));
            return result;
        } catch (Throwable ignored) {
            return new HashSet<>();
        }
    }

    private static boolean isLogicalFont(String name) {
        return "SansSerif".equalsIgnoreCase(name)
                || "Serif".equalsIgnoreCase(name)
                || "Monospaced".equalsIgnoreCase(name)
                || "Dialog".equalsIgnoreCase(name)
                || "DialogInput".equalsIgnoreCase(name);
    }
}
