package com.konqasasas.localreplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigManager {

    private final Path configFile;
    private final Logger logger;
    private final Gson gson;

    public ConfigManager(Path configFile, Logger logger) {
        if (configFile == null) {
            throw new IllegalArgumentException("configFile cannot be null");
        }
        this.configFile = configFile;
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public LocalReplayConfig load() {
        if (!Files.isRegularFile(configFile)) {
            LocalReplayConfig defaults = LocalReplayConfig.defaults();
            save(defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            LocalReplayConfig loaded = gson.fromJson(reader, LocalReplayConfig.class);
            if (loaded == null) {
                throw new IllegalStateException("Config root was null");
            }

            boolean repaired = loaded.normalize();
            if (repaired) {
                warn("Invalid Local Replay config values were reset to defaults.", null);
                save(loaded);
            }
            return loaded;
        } catch (IOException | RuntimeException exception) {
            warn("Failed to load Local Replay config. Defaults will be used.", exception);
            return LocalReplayConfig.defaults();
        }
    }

    public boolean save(LocalReplayConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        config.normalize();

        Path parent = configFile.getParent();
        Path tempFile = configFile.resolveSibling(configFile.getFileName().toString() + ".tmp");

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }

            moveReplacing(tempFile, configFile);
            return true;
        } catch (IOException exception) {
            warn("Failed to save Local Replay config.", exception);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Best effort cleanup only.
            }
            return false;
        }
    }

    public Path getConfigFile() {
        return configFile;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void warn(String message, Throwable throwable) {
        if (logger == null) {
            return;
        }
        if (throwable == null) {
            logger.warn(message);
        } else {
            logger.warn(message, throwable);
        }
    }
}
