package com.movtery.zalithlauncher.plugins.driver.mojo

import java.io.File

/** AdrenoTools-format Vulkan driver record. A null hash denotes the launcher/system default. */
data class MojoVulkanDriver(
    val hash: String?,
    val name: String,
    val description: String,
    val author: String,
    val driverVersion: String,
    val libraryName: String?,
    val schemaVersion: Int,
    val minApi: Int,
    val root: File?,
    val builtIn: Boolean = false,
) {
    fun detailLine(): String = if (builtIn) description else listOf(author, driverVersion).filter { it.isNotBlank() }.joinToString(" • ")
}

sealed class MojoDriverInstallResult {
    data class Success(val driver: MojoVulkanDriver, val replaced: Boolean) : MojoDriverInstallResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : MojoDriverInstallResult()
}
