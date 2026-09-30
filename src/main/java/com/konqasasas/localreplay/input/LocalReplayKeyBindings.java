package com.konqasasas.localreplay.input;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

public final class LocalReplayKeyBindings {

    public static final String CATEGORY = "Local Replay";

    public static final KeyBinding OPEN_LOCAL_REPLAY = new KeyBinding(
            "Open Local Replay",
            Keyboard.KEY_G,
            CATEGORY
    );

    public static final KeyBinding OPEN_REPLAY_LIBRARY = new KeyBinding(
            "Open Replay Library",
            Keyboard.KEY_O,
            CATEGORY
    );

    private static boolean registered;

    private LocalReplayKeyBindings() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ClientRegistry.registerKeyBinding(OPEN_LOCAL_REPLAY);
        ClientRegistry.registerKeyBinding(OPEN_REPLAY_LIBRARY);
        registered = true;
    }
}
