package com.movtery.zalithlauncher.plugins.driver.mojo

import android.content.Context
import android.util.Log
import com.movtery.zalithlauncher.setting.AllSettings
import git.artdeell.mojoexec.MojoExec

/** Configure Mojo's Vulkan loader before LWJGL/Zink first opens libvulkan.so. */
object MojoVulkanDriverBridge {
    private const val TAG = "MojoVulkanDriverBridge"

    /**
     * Call once at game startup, before initGraphicAndSoundEngine()/launchJavaVM().
     * useCustomVulkan=false resets the native selector so the Android system loader is used.
     * The native loader caches its first Vulkan handle; force-stop Zalith after changing drivers.
     */
    @JvmStatic
    fun configureForLaunch(context: Context, useCustomVulkan: Boolean, useSystemVulkan: Boolean) {
        try {
            if (!useCustomVulkan || useSystemVulkan) {
                MojoExec.configureCustomVulkanDriver(null, null)
                return
            }
            val selected = MojoVulkanDriverManager.getPreferredDriver(context)
            val library = selected.libraryName
            val root = selected.root?.absolutePath
            if (library == null) {
                MojoExec.configureCustomVulkanDriver(null, null)
                return
            }
            if (root != null && !java.io.File(root, library).isFile) {
                Log.e(TAG, "Selected driver library is missing: $root/$library; falling back to system Vulkan")
                MojoExec.configureCustomVulkanDriver(null, null)
                return
            }
            if (root == null && !java.io.File(context.applicationInfo.nativeLibraryDir, library).isFile) {
                Log.e(TAG, "Built-in Vulkan library is missing: $library; falling back to system Vulkan")
                MojoExec.configureCustomVulkanDriver(null, null)
                return
            }
            MojoExec.configureCustomVulkanDriver(library, root)
            MojoExec.preloadVulkan()
            Log.i(TAG, "Configured Mojo Vulkan driver ${selected.name} (${library})")
        } catch (error: LinkageError) {
            // Do not prevent the game from starting if the native bridge is unavailable.
            Log.e(TAG, "Mojo Vulkan bridge unavailable; leaving system Vulkan enabled", error)
            try { MojoExec.configureCustomVulkanDriver(null, null) } catch (_: Throwable) { }
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to configure custom Vulkan driver; leaving system Vulkan enabled", error)
            try { MojoExec.configureCustomVulkanDriver(null, null) } catch (_: Throwable) { }
        }
    }
}
