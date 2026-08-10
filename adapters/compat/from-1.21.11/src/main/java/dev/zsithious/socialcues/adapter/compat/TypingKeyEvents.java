package dev.zsithious.socialcues.adapter.compat;

import java.util.function.IntConsumer;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.gui.screen.Screen;

/**
 * DESIGN.md §14 P7 — the {@code fabric-screen-api-v1} 3.x form of "tell me a
 * key was pressed on this screen". See the {@code from-1.21} copy of this
 * class for the full {@code javap} measurement of the seam and for why the
 * normalised {@link IntConsumer} signature below is the shape {@code
 * mc-shared} gets to see; only the one registration line differs between the
 * two copies.
 *
 * <p>Here the callback is {@code afterKeyPress(Screen, KeyInput)}, and {@code
 * KeyInput.key()} is the GLFW key code the 2.x form passed as its second
 * argument.
 *
 * <p><b>Privacy (DESIGN.md §10.1):</b> {@code KeyInput} is deliberately not
 * handed onwards — only the key code it carries. The event object is a
 * platform type that stops at this class, so no shared or bucket source can
 * reach anything text-shaped through it.
 */
public final class TypingKeyEvents {

    private TypingKeyEvents() {
    }

    /**
     * Registers {@code onKeyCode} to run after every key press on {@code
     * screen}, handing it the GLFW key code.
     */
    public static void afterKeyPress(Screen screen, IntConsumer onKeyCode) {
        ScreenKeyboardEvents.afterKeyPress(screen)
                .register((s, key) -> onKeyCode.accept(key.key()));
    }
}
