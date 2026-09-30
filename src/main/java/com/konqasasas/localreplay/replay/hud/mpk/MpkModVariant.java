package com.konqasasas.localreplay.replay.hud.mpk;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Distinguishes the original MPK Mod 1.x from MPKMod2. Both use the same
 * Forge mod id ("mpkmod"), so Loader.isModLoaded alone is not sufficient.
 */
public enum MpkModVariant {
    NONE,
    MPK1,
    MPK2;

    public static MpkModVariant detect(Path minecraftDirectory) {
        if (!Loader.isModLoaded("mpkmod")) return NONE;

        String version = loadedVersion();
        int major = parseMajor(version);
        if (major >= 2) return MPK2;
        if (major == 1) return MPK1;

        // Fail-safe for unusual/dev builds with an unresolved version string.
        // Prefer the modern config when it is present because MPKMod2 may be
        // installed on a profile that still contains a stale MPK1 cfg file.
        if (minecraftDirectory != null) {
            Path mpk2 = minecraftDirectory.resolve("config").resolve("mpk").resolve("config").resolve("mpk-config.json");
            if (Files.isRegularFile(mpk2)) return MPK2;

            Path mpk1 = minecraftDirectory.resolve("config").resolve("mpkmod.cfg");
            if (Files.isRegularFile(mpk1)) return MPK1;
        }

        return NONE;
    }

    private static String loadedVersion() {
        try {
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            ModContainer container = mods == null ? null : mods.get("mpkmod");
            return container == null ? null : container.getVersion();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int parseMajor(String version) {
        if (version == null) return -1;
        String value = version.trim();
        int start = -1;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) return -1;
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
