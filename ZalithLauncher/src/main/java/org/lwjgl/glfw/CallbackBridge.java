package org.lwjgl.glfw;

import android.content.ClipData;
import android.content.ClipDescription;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.game.sdl.SdlBridge;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.MainActivity;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static boolean isGrabbing = false;
    private static final ArrayList<GrabListener> grabListeners = new ArrayList<>();
    
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;
    
    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    /** Accumulated SDL mouse button state (SDL wants MotionEvent.getButtonState() semantics). */
    private static int sMouseButtonState = 0;
    /** Skip the next relative mouse delta (the virtual cursor jumps when the grab state changes). */
    private static volatile boolean sSdlSkipDelta = true;

    // ---- SDL launcher integration ----
    // Called (through JNI) by the game JVM when it initialises SDL; see sdl_hook.c / SDLInit.
    public static final int NOTIF_TYPE_SDL = 0;
    public static final int ACTION_INIT_LAUNCHER_INTEGRATION = 0;
    public static final int ACTION_SEND_TEXTBOX_RECT = 1;

    /** Signature is looked up by native code as notifyLauncher(I[I)Z - do not change. */
    @SuppressWarnings("unused")
    @Keep
    public static boolean notifyLauncher(int type, int... action) {
        if (action == null || action.length == 0) {
            Logging.w("SDL", "SDL notification has no action");
            return false;
        }
        if (type != NOTIF_TYPE_SDL) return false;
        if (action[0] == ACTION_INIT_LAUNCHER_INTEGRATION) {
            if (!SdlBridge.markSdlInitialized()) {
                return true; // already set up
            }
            try {
                Logging.i("SDL", "loading SDL3");
                System.loadLibrary("SDL3");
                Logging.i("SDL", "loading SDL2");
                System.loadLibrary("SDL2");
                Logging.i("SDL", "setting up SDL JNI");
                SdlBridge.setupJNI();
                SdlBridge.setSdlEnabled(true);
                SDLSurface surface = SDLActivity.getSDLSurface();
                if (surface != null) {
                    surface.surfaceChanged();
                    if (windowWidth > 0 && windowHeight > 0) {
                        surface.nativeResize(windowWidth, windowHeight);
                    }
                }
                Logging.i("SDL", "SDL support enabled!");
                return true;
            } catch (Throwable e) {
                SdlBridge.setSdlEnabled(false);
                SdlBridge.clearSdlInitialized();
                StringWriter trace = new StringWriter();
                e.printStackTrace(new PrintWriter(trace));
                Logging.e("SDL", "SDL launcher integration is unavailable:\n" + trace);
            }
        }
        return false;
    }

    private static boolean isTextEventChar(char keychar, int modifiers) {
        if (Character.isISOControl(keychar)) return false;
        return (modifiers & LwjglGlfwKeycode.GLFW_MOD_CONTROL) == 0;
    }
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sChoreographer.postFrameCallbackDelayed(l -> putMouseEventWithCoords(button, false, x, y), 33);
    }
    
    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        final float prevX = mouseX, prevY = mouseY;
        mouseX = x;
        mouseY = y;
        nativeSendCursorPos(mouseX, mouseY);
        if (!SdlBridge.getSdlEnabled()) return;
        if (isGrabbing) {
            // Grabbed (camera control): SDL relative mouse mode wants deltas, not positions
            if (sSdlSkipDelta) {
                sSdlSkipDelta = false;
                return;
            }
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x - prevX, y - prevY, true);
        } else {
            sSdlSkipDelta = true;
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x, y, false);
        }
    }

    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        // TODO CHECK: This may cause input issue, not receive input!
        if(keycode != 0)  nativeSendKey(keycode,scancode,isDown ? 1 : 0, modifiers);
        if(isDown && keychar != '\u0000') {
            nativeSendCharMods(keychar,modifiers);
            nativeSendChar(keychar);
        }
        if (!SdlBridge.getSdlEnabled()) return;
        int androidKeycode = EfficientAndroidLWJGLKeycode.getSdlAndroidKeycode(keycode);
        if (androidKeycode == KeyEvent.KEYCODE_UNKNOWN) return;
        try {
            if (isDown) {
                SDLActivity.onNativeKeyDown(androidKeycode);
                // The game only inserts characters from SDL_EVENT_TEXT_INPUT; a key down alone types nothing
                if (isTextEventChar(keychar, modifiers) && SDLActivity.isSDLTextInputActive()) {
                    SDLActivity.onNativeTextInput(String.valueOf(keychar));
                }
            } else {
                SDLActivity.onNativeKeyUp(androidKeycode);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void sendChar(char keychar, int modifiers){
        nativeSendCharMods(keychar,modifiers);
        nativeSendChar(keychar);
        if (!SdlBridge.getSdlEnabled()) return;
        // Characters from the soft keyboard may only enter the game as SDL text input,
        // never as synthesized key presses (those would trigger key bindings)
        if (isTextEventChar(keychar, modifiers) && SDLActivity.isSDLTextInputActive()) {
            SDLActivity.onNativeTextInput(String.valueOf(keychar));
        }
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
        if (!SdlBridge.getSdlEnabled()) return;
        int aKey = -1;
        switch (button) {
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT:
                aKey = MotionEvent.BUTTON_PRIMARY;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT:
                aKey = MotionEvent.BUTTON_SECONDARY;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE:
                aKey = MotionEvent.BUTTON_TERTIARY;
                break;
            // Back/forward are swapped on SDL's side
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5:
                aKey = MotionEvent.BUTTON_BACK;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4:
                aKey = MotionEvent.BUTTON_FORWARD;
                break;
        }
        if (aKey != -1) {
            if (isDown) {
                sMouseButtonState |= aKey;
            } else {
                sMouseButtonState &= ~aKey;
            }
            SDLActivity.onNativeMouse(sMouseButtonState, isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, mouseX, mouseY, false);
        }
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }
    
    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendScroll(xoffset, yoffset);
        if (!SdlBridge.getSdlEnabled()) return;
        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, (float) xoffset, (float) yoffset, false);
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        switch (type) {
            case CLIPBOARD_COPY:
                MainActivity.GLOBAL_CLIPBOARD.setPrimaryClip(ClipData.newPlainText("Copy", copy));
                return null;

            case CLIPBOARD_PASTE:
                if (MainActivity.GLOBAL_CLIPBOARD.hasPrimaryClip() && MainActivity.GLOBAL_CLIPBOARD.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    return MainActivity.GLOBAL_CLIPBOARD.getPrimaryClip().getItemAt(0).getText().toString();
                } else {
                    return "";
                }

            case CLIPBOARD_OPEN:
                MainActivity.openLink(copy);
                return null;
            default: return null;
        }
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        } if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        } if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        } if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        } if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown){
        switch (keyCode){
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                return;

            case LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
        }
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    private static void onGrabStateChanged(final boolean grabbing) {
        isGrabbing = grabbing;
        sSdlSkipDelta = true;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if(isGrabbing != grabbing) return;

            System.out.println("Grab changed : " + grabbing);
            synchronized (grabListeners) {
                for (GrabListener g : grabListeners) g.onGrabState(grabbing);
            }

        }, 16);

    }
    public static void addGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            listener.onGrabState(isGrabbing);
            grabListeners.add(listener);
        }
    }
    public static void removeGrabListener(GrabListener listener) {
        synchronized (grabListeners) {
            grabListeners.remove(listener);
        }
    }

    @Keep @CriticalNative public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @Keep @CriticalNative private static native boolean nativeSendChar(char codepoint);
    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @Keep @CriticalNative private static native boolean nativeSendCharMods(char codepoint, int mods);
    @Keep @CriticalNative private static native void nativeSendKey(int key, int scancode, int action, int mods);
    // private static native void nativeSendCursorEnter(int entered);
    @Keep @CriticalNative private static native void nativeSendCursorPos(float x, float y);
    @Keep @CriticalNative private static native void nativeSendMouseButton(int button, int action, int mods);
    @Keep @CriticalNative private static native void nativeSendScroll(double xoffset, double yoffset);
    @Keep @CriticalNative private static native void nativeSendScreenSize(int width, int height);
    @Keep public static native void nativeSetWindowAttrib(int attrib, int value);
    @Keep public static native int getCurrentFps();

    static {
        System.loadLibrary("pojavexec");
    }
}

