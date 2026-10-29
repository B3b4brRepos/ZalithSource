package com.movtery.zalithlauncher.plugins.driver.mojo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.movtery.zalithlauncher.setting.AllSettings
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Mojo-compatible AdrenoTools driver package manager.
 * Packages are installed inside private app storage (not /sdcard, which is noexec),
 * in <dataDir>/vulkan-drivers/<UUID>/ and are selected via AllSettings' preferences.
 */
object MojoVulkanDriverManager {
    private const val TAG = "MojoVulkanDriverManager"
    private const val META = "meta.json"
    private const val SETTING_KEY = "customVulkanDriver"
    private const val MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L
    private const val MAX_EXPANDED_BYTES = 768L * 1024L * 1024L
    private const val MAX_SINGLE_FILE_BYTES = 384L * 1024L * 1024L
    private const val MAX_ENTRIES = 8192
    private const val MAX_META_BYTES = 128 * 1024

    private data class PackageMeta(
        val name: String,
        val description: String,
        val author: String,
        val driverVersion: String,
        val libraryName: String,
        val schemaVersion: Int,
        val minApi: Int,
        val hash: String,
    )

    private fun contextDir(context: Context): File = File(context.applicationInfo.dataDir, "vulkan-drivers")
    private fun selectedHash(): String? = AllSettings.customVulkanDriver.getValue().takeIf { it.isNotBlank() }
    private fun setSelectedHash(hash: String?) {
        AllSettings.customVulkanDriver.put(hash ?: "").save()
    }

    @JvmStatic
    fun isSupportedByDevice(): Boolean = Build.VERSION.SDK_INT >= 28

    private fun looksLikeAdreno(): Boolean {
        val hints = listOf(
            Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.PRODUCT,
            Build.MANUFACTURER, Build.MODEL,
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else "",
            if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "",
        ).joinToString(" ").lowercase()
        return hints.contains("adreno") || hints.contains("qualcomm") || hints.contains("qcom")
    }

    private fun builtIn(context: Context): MojoVulkanDriver {
        val lib = File(context.applicationInfo.nativeLibraryDir, "libvulkan_freedreno.so")
        val adreno = looksLikeAdreno() && isSupportedByDevice() && lib.isFile
        return MojoVulkanDriver(
            hash = null,
            name = "Built-in driver",
            description = if (adreno) "Bundled Turnip / Freedreno" else "Android system Vulkan driver",
            author = "Zalith",
            driverVersion = if (adreno) "Bundled" else "System",
            libraryName = if (adreno) "libvulkan_freedreno.so" else null,
            schemaVersion = 1,
            minApi = 28,
            root = null,
            builtIn = true,
        )
    }

    private fun driverHash(name: String, author: String): String =
        UUID.nameUUIDFromBytes("$name::$author".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun isSafeLibraryName(name: String): Boolean =
        name.isNotBlank() && name.endsWith(".so") && name == File(name).name &&
            !name.contains('/') && !name.contains('\\') && !name.contains("..")

    private fun parseMeta(input: InputStream): PackageMeta {
        val bytes = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                require(total <= MAX_META_BYTES) { "meta.json is too large" }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        val obj = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val name = obj.optString("name", "").trim()
        val author = obj.optString("author", "").trim()
        val lib = obj.optString("libraryName", "").trim()
        val schema = obj.optInt("schemaVersion", 0)
        val minApi = obj.optInt("minApi", 28)
        require(name.isNotBlank()) { "meta.json is missing name" }
        require(author.isNotBlank()) { "meta.json is missing author" }
        require(isSafeLibraryName(lib)) { "meta.json libraryName must be a root-level .so file" }
        require(schema > 0) { "Unsupported or missing driver schemaVersion" }
        require(minApi >= 28) { "Custom Vulkan drivers require Android API 28+" }
        require(Build.VERSION.SDK_INT >= minApi) { "This driver requires Android API $minApi or newer" }
        return PackageMeta(
            name = name,
            description = obj.optString("description", ""),
            author = author,
            driverVersion = obj.optString("driverVersion", ""),
            libraryName = lib,
            schemaVersion = schema,
            minApi = minApi,
            hash = driverHash(name, author),
        )
    }

    private fun parseInstalled(root: File): MojoVulkanDriver? {
        return try {
            val metaFile = File(root, META)
            if (!root.isDirectory || !metaFile.isFile) return null
            val meta = FileInputStream(metaFile).use { parseMeta(it) }
            if (meta.hash != root.name) return null
            val mainLib = File(root, meta.libraryName)
            if (!mainLib.isFile || mainLib.length() <= 0L) return null
            MojoVulkanDriver(
                meta.hash, meta.name, meta.description, meta.author, meta.driverVersion,
                meta.libraryName, meta.schemaVersion, meta.minApi, root,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Ignoring invalid driver in ${root.absolutePath}", t)
            null
        }
    }

    @JvmStatic
    fun getDrivers(context: Context): List<MojoVulkanDriver> {
        val result = mutableListOf(builtIn(context))
        val root = contextDir(context)
        val dirs = root.listFiles() ?: return result
        dirs.asSequence().filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { parseInstalled(it) }
            .sortedBy { it.name.lowercase() }
            .forEach { result.add(it) }
        return result
    }

    @JvmStatic
    fun getPreferredDriver(context: Context): MojoVulkanDriver {
        val hash = selectedHash() ?: return builtIn(context)
        val root = File(contextDir(context), hash)
        val loaded = parseInstalled(root)
        if (loaded != null) return loaded
        Log.e(TAG, "Selected driver $hash is missing/invalid; falling back to built-in/system")
        setSelectedHash(null)
        return builtIn(context)
    }

    @JvmStatic
    fun isPreferred(driver: MojoVulkanDriver): Boolean = selectedHash() == driver.hash

    @JvmStatic
    fun setPreferred(driver: MojoVulkanDriver) {
        setSelectedHash(if (driver.builtIn) null else driver.hash)
    }

    @JvmStatic
    fun removeDriver(context: Context, driver: MojoVulkanDriver): Boolean {
        val hash = driver.hash ?: return false
        val root = contextDir(context)
        val dir = File(root, hash).canonicalFile
        if (dir.parentFile != root.canonicalFile || !dir.isDirectory) return false
        val wasSelected = selectedHash() == hash
        if (wasSelected) setSelectedHash(null)
        val deleted = deleteTree(dir)
        if (!deleted && wasSelected) setSelectedHash(hash)
        return deleted
    }

    @JvmStatic
    fun installFromUri(context: Context, uri: Uri): MojoDriverInstallResult {
        if (!isSupportedByDevice()) return MojoDriverInstallResult.Failure("Custom drivers require Android 9 (API 28) or newer")
        val app = context.applicationContext
        val tempArchive = File(app.cacheDir, "mojo-driver-${System.nanoTime()}.zip")
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: return MojoDriverInstallResult.Failure("Android could not open the selected file")
            input.use { source ->
                FileOutputStream(tempArchive).use { dest ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ARCHIVE_BYTES) throw IOException("Driver archive exceeds the 1 GiB safety limit")
                        dest.write(buffer, 0, n)
                    }
                    dest.fd.sync()
                }
            }
            return installArchive(app, tempArchive)
        } catch (t: Throwable) {
            Log.e(TAG, "Driver import failed", t)
            return MojoDriverInstallResult.Failure(t.message ?: "Failed to import driver package", t)
        } finally {
            tempArchive.delete()
        }
    }

    private fun installArchive(context: Context, archive: File): MojoDriverInstallResult {
        val meta = try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(META)
                    ?: throw IOException("Invalid driver package: missing root meta.json")
                if (entry.size > MAX_META_BYTES) throw IOException("meta.json is too large")
                val parsed = zip.getInputStream(entry).use { parseMeta(it) }
                val library = zip.getEntry(parsed.libraryName)
                    ?: throw IOException("Driver package does not contain ${parsed.libraryName}")
                if (library.isDirectory || library.size == 0L || library.size > MAX_SINGLE_FILE_BYTES)
                    throw IOException("Invalid Vulkan library entry")
                parsed
            }
        } catch (t: Throwable) {
            return MojoDriverInstallResult.Failure(t.message ?: "Invalid driver archive", t)
        }

        val parent = contextDir(context)
        if (!parent.exists() && !parent.mkdirs()) return MojoDriverInstallResult.Failure("Could not create private driver directory")
        val destination = File(parent, meta.hash)
        val stage = File(parent, ".install-${meta.hash}-${System.nanoTime()}")
        val backup = File(parent, ".backup-${meta.hash}-${System.nanoTime()}")
        var backedUp = false
        try {
            if (!stage.mkdirs()) throw IOException("Could not create staging directory")
            var entries = 0
            var expanded = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) throw IOException("Driver archive contains too many entries")
                    val name = entry.name.replace('\\', '/')
                    if (name.isBlank() || name.startsWith('/') || name.startsWith("~") ||
                        Regex("^[A-Za-z]:").containsMatchIn(name) ||
                        name.split('/').any { it == ".." || it == "." })
                        throw IOException("Unsafe ZIP entry path: ${entry.name}")
                    val out = File(stage, name).canonicalFile
                    val stagePath = stage.canonicalPath + File.separator
                    if (!out.path.startsWith(stagePath)) throw IOException("ZIP entry escaped staging directory")
                    if (entry.isDirectory) {
                        if (!out.exists() && !out.mkdirs()) throw IOException("Could not create ${entry.name}")
                    } else {
                        if (entry.size > MAX_SINGLE_FILE_BYTES) throw IOException("Driver file exceeds size limit: ${entry.name}")
                        out.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create driver subdirectory") }
                        BufferedOutputStream(FileOutputStream(out)).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var single = 0L
                            while (true) {
                                val n = zin.read(buffer)
                                if (n < 0) break
                                single += n
                                expanded += n
                                if (single > MAX_SINGLE_FILE_BYTES || expanded > MAX_EXPANDED_BYTES)
                                    throw IOException("Driver archive expanded beyond the safety limit")
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    zin.closeEntry()
                }
            }
            // Validate staged metadata and the main library before replacing anything on disk.
            val stagedMeta = FileInputStream(File(stage, META)).use { parseMeta(it) }
            require(stagedMeta.hash == meta.hash && stagedMeta.libraryName == meta.libraryName) {
                "Driver metadata changed during extraction"
            }
            require(File(stage, stagedMeta.libraryName).isFile && File(stage, stagedMeta.libraryName).length() > 0L) {
                "Driver files failed post-extraction validation"
            }

            val replacing = destination.exists()
            if (replacing) {
                if (!destination.renameTo(backup)) throw IOException("Could not safely replace existing driver")
                backedUp = true
            }
            if (!stage.renameTo(destination)) throw IOException("Could not commit installed driver")
            val driver = parseInstalled(destination)
            if (driver == null) {
                deleteTree(destination)
                if (backedUp && backup.exists()) backup.renameTo(destination)
                backedUp = false
                throw IOException("Driver failed final validation; previous installation was restored")
            }
            if (backedUp) {
                if (!deleteTree(backup)) Log.w(TAG, "Installed driver, but could not remove backup ${backup.absolutePath}")
                backedUp = false
            }
            Log.i(TAG, "Installed ${driver.name} (${driver.hash}) at ${destination.absolutePath}")
            return MojoDriverInstallResult.Success(driver, replacing)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed installing driver package", t)
            // Roll back a partially committed destination whenever the previous package is available.
            if (backedUp && backup.exists()) {
                if (destination.exists()) deleteTree(destination)
                if (!backup.renameTo(destination)) Log.e(TAG, "Could not restore driver backup ${backup.absolutePath}")
            }
            deleteTree(stage)
            return MojoDriverInstallResult.Failure(t.message ?: "Failed to install driver package", t)
        } finally {
            deleteTree(stage)
            if (backup.exists() && destination.exists()) deleteTree(backup)
        }
    }

    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) if (!deleteTree(child)) return false
        }
        return file.delete()
    }
}
