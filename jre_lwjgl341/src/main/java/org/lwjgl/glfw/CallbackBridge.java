package org.lwjgl.glfw;

public class CallbackBridge {
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;

    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CHAR_MODS = 1001;
    public static final int EVENT_TYPE_CURSOR_ENTER = 1002;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_FRAMEBUFFER_SIZE = 1004;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;
    public static final int EVENT_TYPE_SCROLL = 1007;
    public static final int EVENT_TYPE_WINDOW_SIZE = 1008;

    public static final int ANDROID_TYPE_GRAB_STATE = 0;

    /** Notification type / action used by org.lwjgl.sdl.SDLInit to tell the launcher that SDL is starting. */
    public static final int SDL = 0;
    public static final int INIT = 0;

    public static final boolean INPUT_DEBUG_ENABLED;

    static {
        INPUT_DEBUG_ENABLED = Boolean.parseBoolean(System.getProperty("glfwstub.debugInput", "false"));
    }

    public static void sendData(int type, String data) {
        nativeSendData(false, type, data);
    }
    public static native void nativeSendData(boolean isAndroid, int type, String data);
    public static native boolean nativeSetInputReady(boolean ready);
    public static native String nativeClipboard(int action, byte[] copy);
    public static native void nativeSetGrabbing(boolean grab);

    /** Implemented in libpojavexec: forwards to the launcher's CallbackBridge.notifyLauncher(int, int[]). */
    public static native boolean nativeNotifyLauncher(int type, int... action);

    /**
     * Cursor shape forwarding is not implemented in this launcher (the Android side has no pointer-icon
     * bridge), so this is a no-op. Kept so org.lwjgl.sdl.SDLMouse links unchanged.
     */
    public static void nativeSetCursorShape(int shape) {
    }
}
