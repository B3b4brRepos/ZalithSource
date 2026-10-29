# Zalith 1.4.1.4 — Mojo-style custom Vulkan driver support

This patch uses the Mojo/AdrenoTools package model instead of the earlier restrictive importer that required `libvulkan_freedreno.so`, `liblinkerhook.so`, and `libdl_android.so` inside every driver ZIP.

## Apply

Copy the contents of this archive into the root of the matching ZalithLauncher v1.4.1.4 source checkout, allowing the included `ZalithLauncher/src/main/...` files to merge/replace their same-path source files. Keep the existing Zalith `liblinkerhook.so` and `libdriver_helper.so`; this patch adds separate `libmojolinker.so` and `libmojoexec.so` under `jniLibs/arm64-v8a`.

The patch includes:
- Mojo-compatible AdrenoTools `meta.json` package parsing and `libraryName` selection.
- Secure extraction to app-private storage, plus ADD / REMOVE / USE / IN USE UI.
- Native launch-time driver configuration and preload bridge.
- The Vulkan Driver settings row hooked to the Mojo manager.

## Package expectations

A custom driver ZIP must contain root-level `meta.json` and the root-level `.so` specified by `libraryName`. Additional native dependencies may be included in that ZIP; the package does **not** need to contain all three libraries from the old importer. Driver-specific dependencies and compatibility still matter, and this does not guarantee every arbitrary Vulkan driver will work.

## Build/test status

This is a source patch, not an APK. It has not been built with the full Android Gradle/SDK toolchain or tested on-device. Build `ZalithLauncher:assembleDebug` for `arm64` using the project's required Java/Android SDK/NDK, then test package import, selection, removal, and game launch. Force-stop Zalith after changing drivers because the native Vulkan handle is cached for the process lifetime.

## Notices

See `licenses/` for attribution and upstream license notices. Retain these notices in redistributed builds.
