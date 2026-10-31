import java.security.MessageDigest

plugins {
    java
}

// LWJGL 3.4.1 runtime used for game versions that ship org.lwjgl:lwjgl:3.4.1 or newer
// (Minecraft versions that use SDL3 instead of GLFW).
//
// The module compiles the launcher-side replacements for GLFW / SDL init (these emulate the
// window system on top of the launcher's input bridge), merges them with the unmodified
// lwjgl-glfw / lwjgl-opengl / lwjgl-sdl jars and writes the result into the launcher assets,
// next to the matching natives that come from libs/lwjgl-3.4.1-natives-release.aar.
//
// Output: ZalithLauncher/src/main/assets/app_runtime/lwjgl/3.4.1  (git-ignored, regenerated on every build)

val lwjglVersion = "3.4.1"
group = "org.lwjgl.glfw"

fun writeVersion(file: File, inputs: List<File>) {
    val digest = MessageDigest.getInstance("SHA-1")
    inputs.forEach { input ->
        input.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }
    file.writeText(digest.digest().joinToString("") { "%02x".format(it) })
}

configurations {
    create("lwjglModules") {
        isCanBeResolved = true
    }
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    add("lwjglModules", fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.jar {
    // Modules that are copied unchanged instead of being merged with the patched classes
    val excludedModules = arrayOf(
        "lwjgl-lwjglx.jar",
        "lwjgl.jar",
        "lwjgl-freetype.jar",
        "lwjgl-nanovg.jar",
        "lwjgl-openal.jar",
        "lwjgl-stb.jar",
        "lwjgl-tinyfd.jar",
        "lwjgl-shaderc.jar",
        "lwjgl-spvc.jar",
        "lwjgl-vma.jar",
        "lwjgl-vulkan.jar",
        "lwjgl-spng.jar"
    )

    inputs.files(configurations["lwjglModules"])

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("lwjgl-$lwjglVersion-merged-modules")
    archiveVersion.set("")
    destinationDirectory.set(file("$rootDir/ZalithLauncher/src/main/assets/app_runtime/lwjgl/$lwjglVersion"))

    from({
        val includedModules = configurations["lwjglModules"].filter { dep ->
            !excludedModules.any { it == dep.name }
        }
        // The core lwjgl.jar must be processed first so duplicated META-INF entries of the other
        // modules are ignored (avoids InvalidModuleDescriptorException).
        val coreJar = includedModules.find { it.name == "lwjgl.jar" }
        val jarList = if (coreJar != null) listOf(coreJar) + (includedModules - coreJar) else includedModules
        jarList.map { if (it.isDirectory) it else zipTree(it) }
    })

    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    val versionFile = File(destinationDirectory.get().asFile, "version")
    doLast {
        val excludedModulesFileList = excludedModules.flatMap { fileName ->
            configurations["lwjglModules"].filter { it.name == fileName }
        }
        copy {
            from(excludedModulesFileList)
            into(archiveFile.get().asFile.parentFile)
        }
        writeVersion(versionFile, listOf(archiveFile.get().asFile) + excludedModulesFileList)
    }
    outputs.file(versionFile)
    outputs.files(excludedModules.map { name -> File(destinationDirectory.get().asFile, name) })

    exclude("net/java/openjdk/cacio/ctc/**")
    exclude("module-info.class")
}
