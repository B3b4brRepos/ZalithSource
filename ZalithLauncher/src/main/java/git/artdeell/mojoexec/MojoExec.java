package git.artdeell.mojoexec;

/**
 * Minimal JNI facade for Mojo's native Vulkan override. The corresponding
 * libmojoexec.so is packaged by this port; liblinkerhook.so is renamed to
 * libmojolinker.so in both the ELF and the native loader string to avoid
 * colliding with Zalith's existing liblinkerhook.so.
 */
public final class MojoExec {
    static {
        // Keep the renamed linker hook loaded in the app namespace before native preload tries
        // to acquire it by name. This avoids depending on APK extraction/search-path behavior.
        System.loadLibrary("mojolinker");
        System.loadLibrary("mojoexec");
    }
    private MojoExec() {}
    public static native void configureCustomVulkanDriver(String libraryName, String librarySearchPath);
    public static native void preloadVulkan();
}
