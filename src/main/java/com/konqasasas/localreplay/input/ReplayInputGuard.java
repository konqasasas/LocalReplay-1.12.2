package com.konqasasas.localreplay.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Cuts live gameplay input while a replay UI owns the client.
 *
 * <p>Opening a GuiScreen normally releases most vanilla bindings, but a key
 * that was already held when the replay opened can otherwise remain logically
 * pressed long enough to keep movement/action state alive in the background.
 * Local Replay treats opening replay as a real key release: all KeyBinding
 * state is cleared immediately and again before every client tick while replay
 * is active.</p>
 *
 * <p>Keys/buttons that were physically held during replay are also latched
 * until the user physically releases them. This prevents a held W/mouse button
 * from becoming pressed again simply because Minecraft restores in-game focus
 * when replay closes. A fresh physical press after release works normally.</p>
 *
 * <p>This class changes only transient client input/control state (KeyBinding,
 * MovementInput, and the player's inherited movement-intent fields). It never
 * writes player position/rotation/motion and never sends packets or invokes
 * gameplay actions.</p>
 */
public final class ReplayInputGuard {

    private final Minecraft minecraft;
    private final Set<Integer> blockedUntilRelease = new HashSet<>();
    private boolean replayIsolationActive;

    public ReplayInputGuard(Minecraft minecraft) {
        if (minecraft == null) {
            throw new IllegalArgumentException("minecraft cannot be null");
        }
        this.minecraft = minecraft;
    }

    /** Called at the exact moment replay becomes active. */
    public void beginReplay() {
        replayIsolationActive = true;
        latchPhysicallyHeldBindings();
        KeyBinding.unPressAllKeys();

        // Also clear the already-computed MovementInput immediately. KeyBinding
        // state alone is not sufficient in 1.12.2 because EntityPlayerSP keeps
        // a MovementInput object whose values are refreshed later in the tick.
        neutralizePlayerControlState();
    }

    /**
     * Called from ClientTickEvent.START so stale bindings are cleared before
     * normal player input/update code can consume them for this tick.
     */
    public void beforeClientTick(boolean replayActive) {
        if (replayActive) {
            replayIsolationActive = true;
            latchPhysicallyHeldBindings();
            KeyBinding.unPressAllKeys();
            neutralizePlayerControlState();
            return;
        }

        replayIsolationActive = false;
        suppressBlockedBindingsUntilReleased();
    }


    /**
     * Called from Forge InputUpdateEvent, after vanilla has rebuilt movement
     * input from the current KeyBindings. This is the authoritative cut point:
     * even if another held physical key survived GUI/focus handling, the real
     * player receives no movement/jump/sneak input while replay is active.
     */
    public void neutralizeMovementInput(MovementInput input) {
        if (!replayIsolationActive || input == null) {
            return;
        }

        clearMovementInput(input);

        // EntityPlayerSP only copies MovementInput into the inherited
        // moveForward/moveStrafing/isJumping control fields while the player is
        // Minecraft's current render-view entity. Replay deliberately switches
        // the render-view entity to ReplayCameraEntity, so without clearing
        // these fields the values from the final live tick can remain latched
        // for the entire replay (for example, W held while opening replay).
        // Clear only transient control intent; never touch position, rotation,
        // velocity/motion, or networking.
        neutralizePlayerControlState();
    }

    private void neutralizePlayerControlState() {
        if (!replayIsolationActive) {
            return;
        }

        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }

        clearMovementInput(player.movementInput);
        player.moveForward = 0.0F;
        player.moveStrafing = 0.0F;
        player.setJumping(false);
    }

    private static void clearMovementInput(MovementInput input) {
        if (input == null) {
            return;
        }
        input.moveForward = 0.0F;
        input.moveStrafe = 0.0F;
        input.forwardKeyDown = false;
        input.backKeyDown = false;
        input.leftKeyDown = false;
        input.rightKeyDown = false;
        input.jump = false;
        input.sneak = false;
    }

    /** Called whenever replay ownership ends, including forced cleanup. */
    public void endReplay() {
        if (!replayIsolationActive) {
            return;
        }
        latchPhysicallyHeldBindings();
        replayIsolationActive = false;

        // Clear both held state and queued press counts created while the replay
        // GUI was active. Minecraft may restore focus immediately afterwards;
        // blockedUntilRelease handles that re-poll on the following ticks.
        KeyBinding.unPressAllKeys();
        suppressBlockedBindingsUntilReleased();
    }

    private void latchPhysicallyHeldBindings() {
        if (minecraft.gameSettings == null || minecraft.gameSettings.keyBindings == null) {
            return;
        }

        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            if (binding == null) {
                continue;
            }
            int keyCode = binding.getKeyCode();
            if (keyCode != 0 && isPhysicalInputDown(keyCode)) {
                blockedUntilRelease.add(keyCode);
            }
        }
    }

    private void suppressBlockedBindingsUntilReleased() {
        if (blockedUntilRelease.isEmpty()) {
            return;
        }

        Iterator<Integer> iterator = blockedUntilRelease.iterator();
        while (iterator.hasNext()) {
            int keyCode = iterator.next();
            if (!isPhysicalInputDown(keyCode)) {
                iterator.remove();
                continue;
            }

            KeyBinding.setKeyBindState(keyCode, false);
            drainQueuedPresses(keyCode);
        }
    }

    private void drainQueuedPresses(int keyCode) {
        if (minecraft.gameSettings == null || minecraft.gameSettings.keyBindings == null) {
            return;
        }

        for (KeyBinding binding : minecraft.gameSettings.keyBindings) {
            if (binding == null || binding.getKeyCode() != keyCode) {
                continue;
            }
            while (binding.isPressed()) {
                // Consume any queued presses for a key that belongs to the
                // replay input cut. It becomes eligible again after release.
            }
        }
    }

    private static boolean isPhysicalInputDown(int keyCode) {
        try {
            if (keyCode < 0) {
                int mouseButton = keyCode + 100;
                return mouseButton >= 0 && Mouse.isButtonDown(mouseButton);
            }
            return keyCode > 0
                    && keyCode < Keyboard.KEYBOARD_SIZE
                    && Keyboard.isKeyDown(keyCode);
        } catch (RuntimeException ignored) {
            // Input devices can transiently be unavailable while focus/window
            // state changes. Treat that as released rather than keeping a latch.
            return false;
        }
    }
}
