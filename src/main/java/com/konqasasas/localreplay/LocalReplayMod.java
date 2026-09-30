package com.konqasasas.localreplay;

import com.konqasasas.localreplay.client.ClientEventHandler;
import com.konqasasas.localreplay.config.ConfigManager;
import com.konqasasas.localreplay.config.LocalReplayConfig;
import com.konqasasas.localreplay.input.LocalReplayKeyBindings;
import com.konqasasas.localreplay.gui.font.FontManager;
import com.konqasasas.localreplay.gui.font.TrueTypeRenderer;
import com.konqasasas.localreplay.replay.ReplayController;
import com.konqasasas.localreplay.replay.record.ReplayRecorder;
import com.konqasasas.localreplay.util.LocalReplayPaths;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Mod(
        modid = LocalReplayMod.MOD_ID,
        name = LocalReplayMod.NAME,
        version = LocalReplayMod.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        acceptableRemoteVersions = "*",
        clientSideOnly = true
)
public final class LocalReplayMod {

    public static final String MOD_ID = "localreplay";
    public static final String NAME = "Local Replay";
    public static final String VERSION = "0.1.0";
    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String AUTHOR = "konqasasas";

    private static Logger logger;
    private static LocalReplayPaths paths;
    private static ConfigManager configManager;
    private static LocalReplayConfig config;
    private static ReplayRecorder replayRecorder;
    private static FontManager fontManager;
    private static TrueTypeRenderer trueTypeRenderer;
    private static ReplayController replayController;
    private static ClientEventHandler clientEventHandler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();

        Path configDirectory = event.getModConfigurationDirectory()
                .toPath()
                .toAbsolutePath()
                .normalize();
        Path minecraftDirectory = configDirectory.getParent();
        if (minecraftDirectory == null) {
            minecraftDirectory = configDirectory;
        }

        try {
            paths = LocalReplayPaths.initialize(minecraftDirectory);
        } catch (IOException exception) {
            logger.error("Failed to initialize Local Replay directories. File features may be unavailable.", exception);
            paths = LocalReplayPaths.unchecked(minecraftDirectory);
        }

        configManager = new ConfigManager(paths.getConfigFile(), logger);
        config = configManager.load();

        LocalReplayKeyBindings.register();

        Minecraft minecraft = Minecraft.getMinecraft();
        fontManager = new FontManager(minecraft, logger);
        trueTypeRenderer = new TrueTypeRenderer(minecraft, fontManager);
        replayRecorder = new ReplayRecorder(
                minecraft,
                LocalReplayMod::getConfig,
                logger
        );
        replayController = new ReplayController(
                minecraft,
                replayRecorder,
                LocalReplayMod::getConfig,
                logger
        );
        clientEventHandler = new ClientEventHandler(
                minecraft,
                replayRecorder,
                replayController,
                logger
        );
        MinecraftForge.EVENT_BUS.register(clientEventHandler);

        logger.info("Local Replay initialized. Recording={}, replayLength={}s, interpolation={}, decimalPlaces={}",
                config.isRecording(),
                config.getReplayLengthSeconds(),
                config.isInterpolation(),
                config.getDecimalPlaces());
    }

    public static Logger getLogger() {
        return logger;
    }

    public static LocalReplayPaths getPaths() {
        return paths;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static LocalReplayConfig getConfig() {
        return config;
    }

    public static FontManager getFontManager() {
        return fontManager;
    }

    public static TrueTypeRenderer getTrueTypeRenderer() {
        return trueTypeRenderer;
    }

    public static ReplayRecorder getReplayRecorder() {
        return replayRecorder;
    }

    public static ReplayController getReplayController() {
        return replayController;
    }

    public static ClientEventHandler getClientEventHandler() {
        return clientEventHandler;
    }

    public static void setConfig(LocalReplayConfig newConfig) {
        if (newConfig == null) {
            throw new IllegalArgumentException("newConfig cannot be null");
        }
        config = newConfig;
    }
}
