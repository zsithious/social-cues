package dev.zsithious.socialcues.adapter.compat;

import java.util.function.IntConsumer;

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.gui.screen.Screen;

/**
 * DESIGN.md §14 P7 — the {@code fabric-screen-api-v1} 2.x form of "tell me a
 * key was pressed on this screen".
 *
 * <p>Measured, not guessed ({@code javap} over every {@code
 * fabric-screen-api-v1} jar the twelve rows resolve): the functional interface
 * {@code ScreenKeyboardEvents.AfterKeyPress} is
 * {@code afterKeyPress(Screen, int key, int scancode, int modifiers)} on
 * screen-api 2.0.24 … 2.2.0, and {@code afterKeyPress(Screen, KeyInput)} on
 * 3.1.0+. The 3.x jars are exactly the ones 1.21.9+ resolves, which is why the
 * {@code from-1.21.9} copy of this class exists and this one stops at 1.21.8.
 *
 * <p>Both forms carry the same GLFW key code, so the seam is narrow enough to
 * normalise away completely: callers in {@code mc-shared} see one
 * version-independent {@link IntConsumer} of that code and never name either
 * platform type. That is the whole point — {@code ClientCueCapture} is shared
 * source compiled by all twelve rows, so it cannot mention {@code KeyInput}
 * (absent before 1.21.9) or a four-argument lambda (wrong from 1.21.9 on).
 *
 * <p><b>Privacy (DESIGN.md §10.1):</b> a key <em>code</em> is all that crosses
 * this boundary, never a character, a screen's text field, or the event object
 * that could reach one. {@code checkNoTextAccess} polices this directory
 * exactly like it polices {@code mc-shared} and the buckets.
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
                .register((s, key, scancode, modifiers) -> onKeyCode.accept(key));
    }
}
