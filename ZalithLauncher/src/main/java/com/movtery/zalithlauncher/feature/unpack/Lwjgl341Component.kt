package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Architecture
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream

/**
 * The LWJGL 3.4.1 runtime (jars + matching natives), used for game versions that require
 * org.lwjgl:lwjgl:3.4.1 or newer (the versions that talk to SDL3 instead of GLFW).
 *
 * The jars are produced by the `jre_lwjgl341` module, the natives come from
 * `libs/lwjgl-3.4.1-natives-release.aar`; both end up in the APK assets under [ASSET_ROOT].
 * They are unpacked into `<data dir>/lwjgl341`, side by side with (and never mixed with) the
 * LWJGL 3.3.x files that older versions keep using.
 */
object Lwjgl341Component {
    private const val TAG = "Lwjgl341"
    private const val ASSET_ROOT = "app_runtime/lwjgl/3.4.1"

    /** First LWJGL version (major*100 + minor*10 + patch) that needs this runtime. */
    const val MIN_VERSION = 341

    @JvmStatic
    fun rootDir(): File = File(PathManager.DIR_DATA, "lwjgl341")

    @JvmStatic
    fun nativesDir(): File = File(rootDir(), "natives/${abiDirName()}")

    private fun abiDirName(): String = when (Architecture.getDeviceArchitecture()) {
        Architecture.ARCH_ARM64 -> "arm64-v8a"
        Architecture.ARCH_ARM -> "armeabi-v7a"
        Architecture.ARCH_X86_64 -> "x86_64"
        else -> "x86"
    }

    /** Colon separated class path of the 3.4.1 jars: core first, merged patch jar second, the rest after. */
    @JvmStatic
    fun classPath(): String {
        val jars = rootDir().listFiles { f -> f.isFile && f.name.endsWith(".jar") } ?: return ""
        fun order(name: String) = when {
            name == "lwjgl.jar" -> 0
            name.endsWith("-merged-modules.jar") -> 1
            else -> 2
        }
        return jars.filter { it.name != "lwjgl-lwjglx.jar" } // LWJGL2 bridge, not needed for 3.4.1 versions
            .sortedWith(compareBy({ order(it.name) }, { it.name }))
            .joinToString(":") { it.absolutePath }
    }

    @JvmStatic
    fun isInstalled(): Boolean =
        File(nativesDir(), "liblwjgl.so").isFile && File(rootDir(), "lwjgl.jar").isFile

    private fun assetVersion(context: Context): String? = runCatching {
        context.assets.open("$ASSET_ROOT/version").use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }.getOrNull()

    private fun installedVersion(): String? = runCatching {
        File(rootDir(), "version").takeIf { it.isFile }?.readText()?.trim()
    }.getOrNull()

    /** False when this build does not contain the 3.4.1 assets at all (module not built). */
    @JvmStatic
    fun isBundled(context: Context): Boolean = assetVersion(context) != null

    @JvmStatic
    fun isUpToDate(context: Context): Boolean {
        val bundled = assetVersion(context) ?: return true // nothing to install
        return bundled == installedVersion() && isInstalled()
    }

    /** Re-extracts the component when the bundled version differs or files are missing. */
    @JvmStatic
    @Synchronized
    fun ensureInstalled(context: Context) {
        if (isUpToDate(context)) return
        unpack(context)
    }

    @JvmStatic
    @Synchronized
    fun unpack(context: Context) {
        val version = assetVersion(context) ?: throw IllegalStateException(
            "LWJGL 3.4.1 assets are missing from this build (is the jre_lwjgl341 module built?)"
        )
        Logging.i(TAG, "Unpacking LWJGL $version to ${rootDir()}")
        val root = rootDir()
        if (root.exists()) FileUtils.deleteDirectory(root)
        root.mkdirs()
        copyAssetDir(context, ASSET_ROOT, root)
        // version file last, so an interrupted extraction is retried on the next launch
        File(root, "version").writeText(version)
        if (!isInstalled()) {
            throw IllegalStateException("LWJGL 3.4.1 extraction finished but liblwjgl.so is missing in ${nativesDir()}")
        }
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val names = context.assets.list(assetPath) ?: return
        target.mkdirs()
        for (name in names) {
            val childAsset = "$assetPath/$name"
            val children = context.assets.list(childAsset)
            if (children != null && children.isNotEmpty()) {
                copyAssetDir(context, childAsset, File(target, name))
            } else {
                if (name == "version" && assetPath == ASSET_ROOT) continue
                context.assets.open(childAsset).use { input ->
                    FileOutputStream(File(target, name)).use { out -> input.copyTo(out) }
                }
            }
        }
    }
}

class UnpackLwjgl341Task(private val context: Context) : AbstractUnpackTask() {
    private val bundled = Lwjgl341Component.isBundled(context)

    /** True when this build has no 3.4.1 assets: the item is then simply not shown. */
    fun isCheckFailed() = !bundled

    override fun isNeedUnpack(): Boolean = bundled && !Lwjgl341Component.isUpToDate(context)

    override fun run() {
        listener?.onTaskStart()
        try {
            Lwjgl341Component.unpack(context)
        } finally {
            listener?.onTaskEnd()
        }
    }
}
