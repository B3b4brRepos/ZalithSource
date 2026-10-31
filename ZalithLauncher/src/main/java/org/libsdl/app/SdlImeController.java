package org.libsdl.app;

import android.os.Handler;
import android.os.Looper;

import com.movtery.zalithlauncher.game.sdl.SdlBridge;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;

/**
 * Zalith 1 version of the SDL text input / soft keyboard controller.
 *
 * SDL tells us (through {@link SDLActivity#showTextInput}) when the game focuses a text field.
 * We remember that, and show the launcher's own {@link TouchCharInput} keyboard; the characters it
 * produces travel through CallbackBridge.sendChar / sendKeycode, which forward them to SDL as
 * text input events while {@link #isTextInputActive()} is true.
 */
final class SdlImeController {
    enum Source { GAME, LAUNCHER, BACK }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean sTextInputActive;
    private static volatile boolean sKeyboardShown;

    private SdlImeController() {}

    /** SDL asked for text input (or the launcher forced it) and has not stopped it since. */
    static boolean isTextInputActive() {
        return sTextInputActive || SdlBridge.isNativeTextInputActive();
    }

    /** Whether characters can currently be delivered to the game as text. */
    static boolean isInputAccepted() {
        return SdlBridge.getSdlEnabled() && isTextInputActive();
    }

    static boolean isEditAvailable() {
        return MainActivity.touchCharInput != null;
    }

    static boolean isKeyboardShown() {
        return sKeyboardShown;
    }

    static void reset() {
        sTextInputActive = false;
        sKeyboardShown = false;
    }

    static void requestShow(Source source) {
        if (source == Source.LAUNCHER) {
            // Launcher forced it: make sure SDL accepts text even if the game did not ask for it
            sTextInputActive = SdlBridge.setNativeTextInputActive(true) || sTextInputActive;
        }
        show();
    }

    /** Called by SDL (through JNI) when the game focuses a text input. */
    static boolean requestShow(Source source, int inputType, int x, int y, int w, int h) {
        sTextInputActive = true;
        if (SdlBridge.getSdlImeAutoShowEnabled() || source != Source.GAME) {
            show();
        }
        return true;
    }

    static void requestHide(Source source) {
        if (source == Source.LAUNCHER) {
            SdlBridge.setNativeTextInputActive(false);
        }
        if (source == Source.GAME || source == Source.LAUNCHER) {
            sTextInputActive = false;
        }
        hide();
    }

    /** Keyboard visibility as reported by the window insets listener. */
    static void notifyVisibilityChanged(boolean visible) {
        sKeyboardShown = visible;
    }

    private static void show() {
        MAIN.post(() -> {
            TouchCharInput input = MainActivity.touchCharInput;
            if (input != null && !input.isEnabled()) {
                input.enable();
            }
            sKeyboardShown = true;
        });
    }

    private static void hide() {
        MAIN.post(() -> {
            TouchCharInput input = MainActivity.touchCharInput;
            if (input != null && input.isEnabled()) {
                input.disable();
            }
            sKeyboardShown = false;
        });
    }
}
