package com.movtery.zalithlauncher.game.sdl;

import android.app.Activity;
import android.view.Surface;
import android.view.ViewGroup;

import androidx.annotation.Keep;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.lang.ref.WeakReference;

/**
 * Owns the SDL integration state shared by the launcher (Android VM) and the game JVM.
 * Static natives are implemented in libpojavexec (input_bridge_v3.c).
 */
@Keep
public final class SdlBridge {
    private SdlBridge() {}

    private static volatile boolean sSdlEnabled = false;
    private static boolean sJniReady = false;
    private static boolean sSdlInitialized = false;
    private static WeakReference<Activity> sActivityRef;
    private static Surface sCurrentSurface;
    private static Object sCurrentSource;

    public static boolean getSdlEnabled() {
        return sSdlEnabled;
    }

    public static void setSdlEnabled(boolean enabled) {
        sSdlEnabled = enabled;
    }

    /** Whether SDL asking for text input should pop the launcher keyboard up by itself. */
    public static boolean getSdlImeAutoShowEnabled() {
        return true;
    }

    @Keep
    public static synchronized boolean setupJNI() {
        if (sJniReady) return true;
        SDL.setupJNI();
        sJniReady = true;
        return true;
    }

    /** @return true the first time it is called after {@link #reset()} */
    @Keep
    public static synchronized boolean markSdlInitialized() {
        if (sSdlInitialized) return false;
        sSdlInitialized = true;
        return true;
    }

    @Keep
    public static synchronized void clearSdlInitialized() {
        sSdlInitialized = false;
    }

    /** Called on the UI thread when the game surface becomes available. */
    public static void prepareSurface(Activity activity, Surface surface, ViewGroup layout, Object source) {
        sActivityRef = new WeakReference<>(activity);
        sCurrentSurface = surface;
        sCurrentSource = source;
        if (SDLActivity.getSDLSurface() == null) {
            SDL.initialize();
            SDL.setContext(activity);
            SDLActivity.externalInitialize(new SDLSurface(activity), layout, surface);
        } else {
            SDLSurface.setNativeSurface(surface);
        }
    }

    /** @return true if this destroy call belongs to the surface registered with {@link #prepareSurface} */
    public static boolean beginSurfaceDestroy(Object source, Surface surface) {
        return source != null && sCurrentSource == source && surface != null && sCurrentSurface == surface;
    }

    public static void unregisterSurface(Surface surface) {
        if (surface != null && sCurrentSurface == surface) {
            sCurrentSurface = null;
            sCurrentSource = null;
        }
    }

    /** Full teardown, called when the game activity goes away. */
    public static synchronized void reset() {
        sCurrentSurface = null;
        sCurrentSource = null;
        sActivityRef = null;
        sJniReady = false;
        sSdlInitialized = false;
        sSdlEnabled = false;
        SDLSurface.clearNativeSurface();
        SDL.initialize();
    }

    /** True once the game has created its first SDL window (i.e. it renders through SDL). */
    public static native boolean isSdlRenderActive();

    public static native boolean setNativeTextInputActive(boolean active);

    public static native boolean isNativeTextInputActive();
}
