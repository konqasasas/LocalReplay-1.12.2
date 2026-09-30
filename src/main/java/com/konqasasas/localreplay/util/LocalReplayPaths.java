package com.konqasasas.localreplay.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalReplayPaths {

    public static final String ROOT_DIRECTORY_NAME = "LocalReplay";
    public static final String REPLAYS_DIRECTORY_NAME = "replays";
    public static final String CONFIG_DIRECTORY_NAME = "config";
    public static final String CONFIG_FILE_NAME = "localreplay.json";

    private final Path rootDirectory;
    private final Path replaysDirectory;
    private final Path configDirectory;
    private final Path configFile;

    private LocalReplayPaths(Path minecraftDirectory) {
        this.rootDirectory = minecraftDirectory.resolve(ROOT_DIRECTORY_NAME);
        this.replaysDirectory = rootDirectory.resolve(REPLAYS_DIRECTORY_NAME);
        this.configDirectory = rootDirectory.resolve(CONFIG_DIRECTORY_NAME);
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
    }

    public static LocalReplayPaths initialize(Path minecraftDirectory) throws IOException {
        LocalReplayPaths paths = unchecked(minecraftDirectory);
        Files.createDirectories(paths.rootDirectory);
        Files.createDirectories(paths.replaysDirectory);
        Files.createDirectories(paths.configDirectory);
        return paths;
    }

    public static LocalReplayPaths unchecked(Path minecraftDirectory) {
        if (minecraftDirectory == null) {
            throw new IllegalArgumentException("minecraftDirectory cannot be null");
        }
        return new LocalReplayPaths(minecraftDirectory.toAbsolutePath().normalize());
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Path getReplaysDirectory() {
        return replaysDirectory;
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }

    public Path getConfigFile() {
        return configFile;
    }
}
